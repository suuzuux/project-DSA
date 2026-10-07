package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.dto.community.CommunityAuthorView;
import megane6.weplanet.domain.dto.community.CommunityJoinInfo;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.portal.ArtistProfileRepository;
import megane6.weplanet.service.FileStorageService;
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
	private final ApplicationEventPublisher eventPublisher; // [배지] 활동 알림 발행용
	// 탈퇴 시 그 커뮤니티의 팔로우 관계도 함께 지우려고 리포지토리를 직접 쓴다
	// (UserFollowService 는 이 서비스에 의존하므로, 서비스를 쓰면 순환 의존이 생긴다).
	private final UserFollowRepository userFollowRepository;
	
	// 커뮤니티 가입 - 가입 정보와 커뮤니티별 프로필(닉네임·소개·사진)을 community_members 한 행으로 같이 저장한다.
	@Transactional
	public void join(User fan, Long artistId, String nickname, String bio,
					 MultipartFile avatar, MultipartFile background) {
		User artist = userRepository.findById(artistId)
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
		if (artist.getRole() != Role.ARTIST) {
			throw new IllegalArgumentException("error.community.artistNotFound");
		}
		// 활성화 전(PENDING_ACTIVATION)이거나 정지·탈퇴된 아티스트의 커뮤니티에는 가입할 수 없다
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
		
		// 이미지 형식(jpg/png/gif/webp)·크기를 검증한 뒤 서버가 정한 확장자로 저장한다.
		// 가입이 취소되면(뒤이은 배경 사진 검증 실패 등) 먼저 저장한 사진 파일도 지운다.
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
		
		// [배지] 가입 완료 알림 -> 첫 가입 배지 + 가입 전 활동 배지 확인 (트랜잭션 커밋 후 실행됨)
		eventPublisher.publishEvent(new BadgeActivityEvent(
				fan.getId(), artistId, BadgeActivityEvent.Activity.COMMUNITY_JOINED
		));
	}

	/**
	 * 이미 가입돼 있으면 아무 것도 하지 않고, 없으면 최소 프로필로 가입 처리.
	 * 에이전시 자동 가입 등 멱등성이 필요한 경로에서 사용.
	 */
	// 별도 트랜잭션(REQUIRES_NEW) - 같은 가입이 동시에 들어와 한쪽이 유니크 제약으로 실패해도 그쪽만 롤백되고,
	// 호출한 쪽(AgencyEnrollmentService)은 "이미 가입됨"으로 넘긴다.
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void ensureJoined(User user, Long artistId, String nickname) {
		if (user == null || artistId == null) {
			return;
		}
		if (communityMemberRepository.existsByFanIdAndArtistId(user.getId(), artistId)) {
			return;
		}
		// 아직 활성화 전인 아티스트는 자동 가입하지 않는다 (join 에서 막히므로 조용히 건너뜀)
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
	
	// 커뮤니티별 프로필 편집 (닉네임 / 소개글 / 프로필 이미지 / 배경 이미지 / 콘텐츠 숨김).
	// 이미지는 삭제 요청이 우선이고, 그다음 새 파일 교체, 둘 다 없으면 기존 이미지를 그대로 둔다.
	@Transactional
	public void editProfile(User fan, Long artistId, String nickname, String bio,
							MultipartFile avatar, MultipartFile background,
							boolean removeAvatar, boolean removeBackground,
							boolean contentHidden) {
		// 가입 행이 곧 이 커뮤니티의 프로필이다
		CommunityMember profile = communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.orElseThrow(() -> new IllegalStateException("error.community.notJoined"));
		// 바뀌는 옛 사진은 저장이 확정된 뒤에 지우고, 새로 올린 사진은 저장이 취소되면 지운다 (cleanUpFilesAfterTransaction)
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
			// 새 파일을 먼저 저장(검증)하고, 옛 파일은 저장이 확정된 뒤에 지운다 - 검증에 실패하면 기존 사진이 그대로 남는다
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
		// 프로필 사진 파일은 탈퇴가 DB 에 확정된 뒤에 지운다 (탈퇴 처리가 실패하면 사진도 그대로 남도록)
		List<String> profileFiles = new ArrayList<>();
		if (member.getAvatarStoredName() != null) profileFiles.add(member.getAvatarStoredName());
		if (member.getBackgroundStoredName() != null) profileFiles.add(member.getBackgroundStoredName());
		cleanUpFilesAfterTransaction(profileFiles, List.of());
		communityMemberRepository.delete(member);
		// 이 커뮤니티 소속 팔로우 관계를 함께 지운다.
		// 단, 팬→아티스트 팔로우(following_id == community_id)는 가입과 무관하게 할 수 있는 것이라 남긴다.
		userFollowRepository.deleteByCommunityIdAndFollowerIdAndFollowingIdNot(artistId, fan.getId(), artistId);
		userFollowRepository.deleteByCommunityIdAndFollowingId(artistId, fan.getId());
	}
	
	// 커뮤니티 페이지에서 "이 커뮤니티에 가입했는지" 판단 - 가입/탭 접근 제어의 기준
	public boolean isJoined(User fan, Long artistId) {
		if (fan == null) return false;
		return communityMemberRepository.existsByFanIdAndArtistId(fan.getId(), artistId);
	}

	// 가입한 커뮤니티 목록. 프로필 유무와 관계없이 community_members 기준.
	public Set<Long> joinedArtistIds(User fan) {
		return joinedAtByArtistId(fan).keySet();
	}

	/** 가입 커뮤니티별 joinedAt. 알림에서 가입 이전 이벤트를 걸러낼 때 쓴다. */
	public Map<Long, LocalDateTime> joinedAtByArtistId(User fan) {
		if (fan == null) return Map.of();
		Map<Long, LocalDateTime> result = new LinkedHashMap<>();
		for (CommunityMember member : communityMemberRepository.findByFanId(fan.getId())) {
			result.put(member.getArtistId(), member.getJoinedAt());
		}
		return result;
	}
	
	// 내 프로필 화면에 계정 아이디 대신 이 커뮤니티 전용 닉네임을 띄우기 위해 씀. 미가입이면 null.
	// 가입 행(CommunityMember)이 곧 커뮤니티별 프로필이다.
	public CommunityMember profileOf(User fan, Long artistId) {
		if (fan == null) return null;
		return communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.orElse(null);
	}

	// PROFILE-03: 계정 가입일이 아니라, 선택한 아티스트 커뮤니티의 가입일과 D+N을 반환한다.
	// 미가입 사용자(아티스트 본인/관리자 포함)는 표시할 D-DAY가 없으므로 null을 반환한다.
	public CommunityJoinInfo joinInfoOf(User fan, Long artistId) {
		if (fan == null) return null;
		return communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.map(member -> CommunityJoinInfo.from(member.getJoinedAt(), LocalDate.now()))
				.orElse(null);
	}
	
	// 커뮤니티 화면에서 작성자 이름을 보여줄 때 쓰는 헬퍼 - 그 커뮤니티 전용 닉네임이 있으면 그걸,
	// 없으면(아티스트 본인, 탈퇴한 회원 등) 계정 닉네임을 쓴다.
	public String displayNickname(User author, Long artistId) {
		if (author == null) {
			return null;
		}
		CommunityMember profile = profileOf(author, artistId);
		return profile != null ? profile.getNickname() : author.getNickname();
	}

	/**
	 * 게시글/댓글 목록을 한 번에 그릴 때 쓰는 "작성자 id → 커뮤니티 닉네임" 맵.
	 * Thymeleaf 맵 키 접근 이슈를 피하려고 String 키로 만든다 (작성자 프로필은 한 쿼리로 읽는다 - authorViewsByAuthorIdKey).
	 */
	public Map<String, String> displayNicknamesByAuthorIdKey(Collection<User> authors, Long artistId) {
		Map<String, String> result = new LinkedHashMap<>();
		authorViewsByAuthorIdKey(authors, artistId).forEach(
				(authorId, view) -> result.put(authorId, view.nickname()));
		return result;
	}

	/**
	 * 게시글 목록에서 사용할 커뮤니티 전용 닉네임과 프로필 이미지 URL을 한 번에 만든다.
	 * 작성자별 조회를 반복하지 않고 현재 페이지 작성자들의 프로필을 한 쿼리로 읽는다.
	 */
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

		// 아티스트 쪽 작성자(솔로 본인/그룹 멤버)는 커뮤니티에 가입하지 않아 가입 프로필이 없다.
		// 대신 커뮤니티 프로필 편집에서 고친 계정별 포털 프로필(artist_profile) 사진을 쓴다. 역시 한 쿼리로 읽는다.
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

	// 디스크의 사진 파일은 DB 와 달리 롤백되지 않아서 트랜잭션 결과를 보고 정리한다.
	// 커밋되면 교체·삭제된 옛 파일(replacedFiles)을, 롤백되면 새로 올린 파일(newFiles)을 지운다 (목록은 호출한 쪽이 채운다).
	private void cleanUpFilesAfterTransaction(List<String> replacedFiles, List<String> newFiles) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			// 트랜잭션 밖에서 불린 경우(테스트 등) - 되돌릴 저장이 없으므로 옛 파일 정리만 하던 방식 그대로
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

	// 포털 프로필 이미지는 업로드 파일명 또는 외부 URL 로 저장된다 (PortalManagementService.toPublicImageUrl 과 같은 규칙)
	private static String toPublicImageUrl(String storedOrUrl) {
		String value = storedOrUrl.trim();
		if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")) {
			return value;
		}
		return "/uploads/" + value;
	}

	// 화면에 프로필 카드(닉네임/소개글/아바타/배경)를 그릴 때 씀 - 가입한 커뮤니티의 프로필을 쿼리 한 번으로 읽는다
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

	// 급상승 커뮤니티 정렬용: 최근 days일 동안 새로 가입한 사람 수 (artistId → 명)
	public Map<Long, Long> countNewMembers(Collection<Long> artistIds, int days) {
		if (artistIds.isEmpty()) return Map.of();
		return communityMemberRepository
				.countNewMembersByArtistIds(artistIds, LocalDateTime.now().minusDays(days))
				.stream()
				.collect(Collectors.toMap(ArtistCount::artistId, ArtistCount::count));
	}
}
