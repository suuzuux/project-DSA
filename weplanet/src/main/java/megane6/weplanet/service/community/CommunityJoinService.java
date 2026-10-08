package megane6.weplanet.service.community;
import megane6.weplanet.service.fan.UserFollowService;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.dto.community.CommunityAuthorView;
import megane6.weplanet.domain.dto.community.CommunityJoinInfo;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.fan.UserFollowRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.portal.ArtistProfileRepository;
import megane6.weplanet.service.main.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityJoinService {
	
	private final CommunityMemberRepository communityMemberRepository;
	private final ArtistProfileRepository artistProfileRepository;
	private final UserRepository userRepository;
	private final FileStorageService fileStorageService;
	private final ApplicationEventPublisher eventPublisher; // [배지] 활동 이벤트 발행
	// 순환 의존을 피하려고 팔로우 리포지토리를 직접 쓴다.
	private final UserFollowRepository userFollowRepository;
	
	// 커뮤니티 가입 - 가입 정보와 커뮤니티 프로필을 한 행으로 저장한다.
	@Transactional
	public void join(User fan, Long artistId, String nickname, String bio,
					 MultipartFile avatar, MultipartFile background) {
		User artist = userRepository.findById(artistId)
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
		if (artist.getRole() != Role.ARTIST) {
			throw new IllegalArgumentException("error.community.artistNotFound");
		}
		// 활성화 전이거나 정지·탈퇴된 아티스트 커뮤니티는 가입할 수 없다.
		if (!artist.isLoginable()) {
			throw new IllegalStateException("error.community.notJoinable");
		}
		if (communityMemberRepository.existsByFanIdAndArtistId(fan.getId(), artistId)) {
			throw new IllegalStateException("error.community.alreadyJoined");
		}
		if (nickname == null || nickname.isBlank()) {
			throw new IllegalArgumentException("error.community.nicknameRequired");
		}
		if (nickname.length() > 10) {
			throw new IllegalArgumentException("error.community.nicknameTooLong");
		}
		if (bio != null && bio.length() > 30) {
			throw new IllegalArgumentException("error.community.bioTooLong");
		}
		
		// 이미지 검증 후 저장하고, 가입이 취소되면 저장한 파일도 지운다.
		List<String> newFiles = new ArrayList<>();
		cleanUpFilesAfterTransaction(List.of(), newFiles);
		String avatarStoredName = (avatar != null && !avatar.isEmpty()) ? fileStorageService.storeImage(avatar) : null;
		if (avatarStoredName != null) newFiles.add(avatarStoredName);
		String backgroundStoredName = (background != null && !background.isEmpty()) ? fileStorageService.storeImage(background) : null;
		if (backgroundStoredName != null) newFiles.add(backgroundStoredName);

		communityMemberRepository.save(CommunityMember.builder()
				.fanId(fan.getId())
				.artistId(artistId)
				.nickname(nickname)
				.bio(bio)
				.avatarStoredName(avatarStoredName)
				.backgroundStoredName(backgroundStoredName)
				.build());
		
		// [배지] 가입 완료 이벤트 (커밋 후 실행)
		eventPublisher.publishEvent(new BadgeActivityEvent(
				fan.getId(), artistId, BadgeActivityEvent.Activity.COMMUNITY_JOINED
		));
	}

	/** 이미 가입돼 있으면 그대로, 없으면 최소 프로필로 가입한다 (멱등). */
	// 별도 트랜잭션 - 동시 가입 충돌 시 그쪽만 롤백되고 호출부는 "이미 가입됨"으로 처리한다.
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void ensureJoined(User user, Long artistId, String nickname) {
		if (user == null || artistId == null) {
			return;
		}
		if (communityMemberRepository.existsByFanIdAndArtistId(user.getId(), artistId)) {
			return;
		}
		// 활성화 전 아티스트는 자동 가입하지 않는다.
		boolean activeArtist = userRepository.findById(artistId).map(User::isLoginable).orElse(false);
		if (!activeArtist) {
			return;
		}
		String safeNickname = (nickname == null || nickname.isBlank()) ? "Member" : nickname.trim();
		if (safeNickname.length() > 10) {
			safeNickname = safeNickname.substring(0, 10);
		}
		join(user, artistId, safeNickname, null, null, null);
	}
	
	// 커뮤니티 프로필 편집 (이미지는 삭제 요청 > 새 파일 > 기존 유지 순).
	@Transactional
	public void editProfile(User fan, Long artistId, String nickname, String bio,
							MultipartFile avatar, MultipartFile background,
							boolean removeAvatar, boolean removeBackground,
							boolean contentHidden) {
		// 가입 행이 곧 커뮤니티 프로필이다.
		CommunityMember profile = communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.orElseThrow(() -> new IllegalStateException("error.community.notJoined"));
		// 옛 사진은 커밋 후, 새 사진은 롤백 시 지운다.
		List<String> replacedFiles = new ArrayList<>();
		List<String> newFiles = new ArrayList<>();
		cleanUpFilesAfterTransaction(replacedFiles, newFiles);

		if (nickname != null && !nickname.isBlank()) {
			if (nickname.length() > 10) {
				throw new IllegalArgumentException("error.community.nicknameTooLong");
			}
			profile.setNickname(nickname);
		}
		if (bio != null) {
			if (bio.length() > 30) {
				throw new IllegalArgumentException("error.community.bioTooLong");
			}
			profile.setBio(bio);
		}
		
		if (removeAvatar) {
			if (profile.getAvatarStoredName() != null) {
				replacedFiles.add(profile.getAvatarStoredName());
			}
			profile.setAvatarStoredName(null);
		} else if (avatar != null && !avatar.isEmpty()) {
			// 새 파일을 먼저 검증·저장하고 옛 파일은 커밋 후 지운다.
			String newAvatar = fileStorageService.storeImage(avatar);
			newFiles.add(newAvatar);
			if (profile.getAvatarStoredName() != null) {
				replacedFiles.add(profile.getAvatarStoredName());
			}
			profile.setAvatarStoredName(newAvatar);
		}

		if (removeBackground) {
			if (profile.getBackgroundStoredName() != null) {
				replacedFiles.add(profile.getBackgroundStoredName());
			}
			profile.setBackgroundStoredName(null);
		} else if (background != null && !background.isEmpty()) {
			String newBackground = fileStorageService.storeImage(background);
			newFiles.add(newBackground);
			if (profile.getBackgroundStoredName() != null) {
				replacedFiles.add(profile.getBackgroundStoredName());
			}
			profile.setBackgroundStoredName(newBackground);
		}
		
		profile.setContentHidden(contentHidden);
		communityMemberRepository.save(profile);
	}
	
	@Transactional
	public void leave(User fan, Long artistId) {
		CommunityMember member = communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.orElseThrow(() -> new IllegalStateException("error.community.notJoined"));
		// 프로필 사진은 탈퇴가 커밋된 뒤 지운다.
		List<String> profileFiles = new ArrayList<>();
		if (member.getAvatarStoredName() != null) profileFiles.add(member.getAvatarStoredName());
		if (member.getBackgroundStoredName() != null) profileFiles.add(member.getBackgroundStoredName());
		cleanUpFilesAfterTransaction(profileFiles, List.of());
		communityMemberRepository.delete(member);
		// 이 커뮤니티의 팔로우 관계를 지운다 (아티스트 팔로우는 유지).
		userFollowRepository.deleteByCommunityIdAndFollowerIdAndFollowingIdNot(artistId, fan.getId(), artistId);
		userFollowRepository.deleteByCommunityIdAndFollowingId(artistId, fan.getId());
	}
	
	// 커뮤니티 가입 여부 (탭 접근 제어 기준)
	public boolean isJoined(User fan, Long artistId) {
		if (fan == null) return false;
		return communityMemberRepository.existsByFanIdAndArtistId(fan.getId(), artistId);
	}

	// 가입한 커뮤니티 id 목록
	public Set<Long> joinedArtistIds(User fan) {
		return joinedAtByArtistId(fan).keySet();
	}

	/** 커뮤니티별 가입 시각 (알림에서 가입 이전 이벤트 제외용) */
	public Map<Long, LocalDateTime> joinedAtByArtistId(User fan) {
		if (fan == null) return Map.of();
		Map<Long, LocalDateTime> result = new LinkedHashMap<>();
		for (CommunityMember member : communityMemberRepository.findByFanId(fan.getId())) {
			result.put(member.getArtistId(), member.getJoinedAt());
		}
		return result;
	}
	
	// 이 커뮤니티 프로필 (미가입이면 null)
	public CommunityMember profileOf(User fan, Long artistId) {
		if (fan == null) return null;
		return communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.orElse(null);
	}

	// 선택한 커뮤니티의 가입일과 D+N (미가입이면 null).
	public CommunityJoinInfo joinInfoOf(User fan, Long artistId) {
		if (fan == null) return null;
		return communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.map(member -> CommunityJoinInfo.from(member.getJoinedAt(), LocalDate.now()))
				.orElse(null);
	}
	
	// 작성자 표시 이름 (커뮤니티 닉네임이 없으면 계정 닉네임).
	public String displayNickname(User author, Long artistId) {
		if (author == null) {
			return null;
		}
		CommunityMember profile = profileOf(author, artistId);
		return profile != null ? profile.getNickname() : author.getNickname();
	}

	/** 작성자 id → 커뮤니티 닉네임 맵 (Thymeleaf 를 위해 String 키). */
	public Map<String, String> displayNicknamesByAuthorIdKey(Collection<User> authors, Long artistId) {
		Map<String, String> result = new LinkedHashMap<>();
		authorViewsByAuthorIdKey(authors, artistId).forEach(
				(authorId, view) -> result.put(authorId, view.nickname()));
		return result;
	}

	/** 작성자별 커뮤니티 닉네임과 프로필 이미지를 한 쿼리로 만든다. */
	public Map<String, CommunityAuthorView> authorViewsByAuthorIdKey(
			Collection<User> authors,
			Long artistId
	) {
		if (authors == null || authors.isEmpty()) {
			return Map.of();
		}

		Map<Long, User> uniqueAuthors = new LinkedHashMap<>();
		for (User author : authors) {
			if (author != null && author.getId() != null) {
				uniqueAuthors.putIfAbsent(author.getId(), author);
			}
		}
		if (uniqueAuthors.isEmpty()) {
			return Map.of();
		}

		Map<Long, CommunityMember> profilesByAuthorId = new HashMap<>();
		if (artistId != null) {
			for (CommunityMember profile : communityMemberRepository.findByArtistIdAndFanIdIn(
					artistId, uniqueAuthors.keySet())) {
				profilesByAuthorId.put(profile.getFanId(), profile);
			}
		}

		// 아티스트 쪽 작성자는 포털 프로필 사진을 쓴다 (한 쿼리로 조회).
		List<Long> artistSideAuthorIds = uniqueAuthors.values().stream()
				.filter(author -> !profilesByAuthorId.containsKey(author.getId()) && author.isArtistSide())
				.map(User::getId)
				.toList();
		Map<Long, String> artistAvatarUrls = new HashMap<>();
		if (!artistSideAuthorIds.isEmpty()) {
			for (Object[] row : artistProfileRepository.findLogoImageUrlsByArtistIds(artistSideAuthorIds)) {
				artistAvatarUrls.put((Long) row[0], toPublicImageUrl(String.valueOf(row[1])));
			}
		}

		Map<String, CommunityAuthorView> result = new LinkedHashMap<>();
		uniqueAuthors.forEach((authorId, author) -> {
			CommunityMember profile = profilesByAuthorId.get(authorId);
			String nickname = profile != null ? profile.getNickname() : author.getNickname();
			String avatarUrl = artistAvatarUrls.get(authorId);
			if (profile != null
					&& !profile.isContentHidden()
					&& profile.getAvatarStoredName() != null
					&& !profile.getAvatarStoredName().isBlank()) {
				avatarUrl = "/uploads/" + profile.getAvatarStoredName();
			}
			result.put(String.valueOf(authorId), new CommunityAuthorView(nickname, avatarUrl));
		});
		return result;
	}

	// 디스크 파일은 롤백되지 않아 커밋이면 옛 파일, 롤백이면 새 파일을 지운다.
	private void cleanUpFilesAfterTransaction(List<String> replacedFiles, List<String> newFiles) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			// 트랜잭션 밖 호출이면 옛 파일만 정리한다.
			replacedFiles.forEach(fileStorageService::delete);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_COMMITTED) {
					replacedFiles.forEach(fileStorageService::delete);
				} else if (status == STATUS_ROLLED_BACK) {
					newFiles.forEach(fileStorageService::delete);
				}
			}
		});
	}

	// 포털 프로필 이미지 URL 변환 (업로드 파일명 또는 외부 URL)
	private static String toPublicImageUrl(String storedOrUrl) {
		String value = storedOrUrl.trim();
		if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")) {
			return value;
		}
		return "/uploads/" + value;
	}

	// 가입한 커뮤니티 프로필을 한 번에 조회
	public Map<Long, CommunityMember> joinedProfilesByArtistId(User fan) {
		if (fan == null) return Map.of();
		Map<Long, CommunityMember> result = new HashMap<>();
		for (CommunityMember profile : communityMemberRepository.findByFanId(fan.getId())) {
			result.put(profile.getArtistId(), profile);
		}
		return result;
	}

	public long countMembers(Long artistId) {
		return communityMemberRepository.countByArtistId(artistId);
	}

	// 급상승 커뮤니티용 최근 days 일 신규 가입자 수 (artistId → 명)
	public Map<Long, Long> countNewMembers(Collection<Long> artistIds, int days) {
		if (artistIds.isEmpty()) return Map.of();
		return communityMemberRepository
				.countNewMembersByArtistIds(artistIds, LocalDateTime.now().minusDays(days))
				.stream()
				.collect(Collectors.toMap(ArtistCount::artistId, ArtistCount::count));
	}
}
