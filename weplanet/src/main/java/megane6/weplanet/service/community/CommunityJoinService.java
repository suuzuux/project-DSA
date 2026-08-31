package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.community.CommunityProfile;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.community.CommunityProfileRepository;
import megane6.weplanet.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommunityJoinService {
	
	private final CommunityMemberRepository communityMemberRepository;
	private final CommunityProfileRepository communityProfileRepository;
	private final UserRepository userRepository;
	private final FileStorageService fileStorageService;
	
	// EXPLORE-03: "선택한 아티스트의 커뮤니티에 가입 후 커뮤니티 프로필 생성"이 한 세트라
	// 가입(community_members)과 프로필 생성(community_profiles)을 트랜잭션 하나로 묶음
	// - 중간에 실패해서 "가입은 됐는데 프로필이 없는" 어중간한 상태가 안 생기게 함.
	@Transactional
	public void join(User fan, Long artistId, String nickname, String bio,
					 MultipartFile avatar, MultipartFile background) {
		User artist = userRepository.findById(artistId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아티스트(id=" + artistId + ")입니다."));
		if (artist.getRole() != Role.ARTIST) {
			throw new IllegalArgumentException("아티스트 계정이 아닙니다(id=" + artistId + ").");
		}
		if (communityMemberRepository.existsByFanIdAndArtistId(fan.getId(), artistId)) {
			throw new IllegalStateException("이미 가입한 커뮤니티입니다.");
		}
		if (nickname == null || nickname.isBlank()) {
			throw new IllegalArgumentException("닉네임을 입력해주세요.");
		}
		if (nickname.length() > 10) {
			throw new IllegalArgumentException("닉네임은 10자 이내로 입력해주세요.");
		}
		if (bio != null && bio.length() > 30) {
			throw new IllegalArgumentException("소개글은 30자 이내로 입력해주세요.");
		}
		
		CommunityMember member = communityMemberRepository.save(CommunityMember.builder()
				.fanId(fan.getId())
				.artistId(artistId)
				.build());
		
		String avatarStoredName = (avatar != null && !avatar.isEmpty()) ? fileStorageService.store(avatar) : null;
		String backgroundStoredName = (background != null && !background.isEmpty()) ? fileStorageService.store(background) : null;
		
		communityProfileRepository.save(CommunityProfile.builder()
				.communityMember(member)
				.nickname(nickname)
				.bio(bio)
				.avatarStoredName(avatarStoredName)
				.backgroundStoredName(backgroundStoredName)
				.build());
	}
	
	// PROFILE-01: 커뮤니티별 프로필 편집 (닉네임 / 소개글 / 프로필 이미지 / 배경 이미지)
	// 이미지 규칙 - 삭제 요청이 최우선이고, 그 다음이 새 파일 교체, 둘 다 없으면 기존 이미지를 그대로 둔다.
	// (화면의 "이미지 삭제하기"가 removeAvatar/removeBackground로 넘어옴)
	@Transactional
	public void editProfile(User fan, Long artistId, String nickname, String bio,
							MultipartFile avatar, MultipartFile background,
							boolean removeAvatar, boolean removeBackground,
							boolean contentHidden) {
		CommunityMember member = communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.orElseThrow(() -> new IllegalStateException("가입하지 않은 커뮤니티입니다."));
		CommunityProfile profile = communityProfileRepository.findByCommunityMember_Id(member.getId())
				.orElseThrow(() -> new IllegalStateException("커뮤니티 프로필이 없습니다."));
		
		if (nickname != null && !nickname.isBlank()) {
			if (nickname.length() > 10) {
				throw new IllegalArgumentException("닉네임은 10자 이내로 입력해주세요.");
			}
			profile.setNickname(nickname);
		}
		if (bio != null) {
			if (bio.length() > 30) {
				throw new IllegalArgumentException("소개글은 30자 이내로 입력해주세요.");
			}
			profile.setBio(bio);
		}
		
		if (removeAvatar) {
			if (profile.getAvatarStoredName() != null) {
				fileStorageService.delete(profile.getAvatarStoredName());
			}
			profile.setAvatarStoredName(null);
		} else if (avatar != null && !avatar.isEmpty()) {
			if (profile.getAvatarStoredName() != null) {
				fileStorageService.delete(profile.getAvatarStoredName());
			}
			profile.setAvatarStoredName(fileStorageService.store(avatar));
		}
		
		if (removeBackground) {
			if (profile.getBackgroundStoredName() != null) {
				fileStorageService.delete(profile.getBackgroundStoredName());
			}
			profile.setBackgroundStoredName(null);
		} else if (background != null && !background.isEmpty()) {
			if (profile.getBackgroundStoredName() != null) {
				fileStorageService.delete(profile.getBackgroundStoredName());
			}
			profile.setBackgroundStoredName(fileStorageService.store(background));
		}
		
		profile.setContentHidden(contentHidden);
		communityProfileRepository.save(profile);
	}
	
	@Transactional
	public void leave(User fan, Long artistId) {
		CommunityMember member = communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.orElseThrow(() -> new IllegalStateException("가입하지 않은 커뮤니티입니다."));
		communityProfileRepository.findByCommunityMember_Id(member.getId()).ifPresent(profile -> {
			if (profile.getAvatarStoredName() != null) fileStorageService.delete(profile.getAvatarStoredName());
			if (profile.getBackgroundStoredName() != null) fileStorageService.delete(profile.getBackgroundStoredName());
			communityProfileRepository.delete(profile);
		});
		communityMemberRepository.delete(member);
	}
	
	// 커뮤니티 페이지에서 "이 커뮤니티에 가입했는지" 판단 - 가입/탭 접근 제어의 기준
	public boolean isJoined(User fan, Long artistId) {
		if (fan == null) return false;
		return communityMemberRepository.existsByFanIdAndArtistId(fan.getId(), artistId);
	}
	
	// 내 프로필 화면에 계정 아이디 대신 이 커뮤니티 전용 닉네임을 띄우기 위해 씀. 미가입이면 null.
	public CommunityProfile profileOf(User fan, Long artistId) {
		if (fan == null) return null;
		return communityMemberRepository.findByFanIdAndArtistId(fan.getId(), artistId)
				.flatMap(member -> communityProfileRepository.findByCommunityMember_Id(member.getId()))
				.orElse(null);
	}
	
	// [닉네임 관리] 커뮤니티 화면에서 작성자 이름을 보여줄 때 공통으로 쓰는 헬퍼.
	// 가입할 때 설정한 커뮤니티 전용 닉네임이 있으면 그걸 쓰고, 없으면(아티스트 본인, 탈퇴한 회원 등)
	// 계정 닉네임으로 대체한다. "가입할 때 닉네임과 글 쓸 때 닉네임이 다르게 보인다"는 문제의 해결 지점.
	public String displayNickname(User author, Long artistId) {
		if (author == null) {
			return null;
		}
		CommunityProfile profile = profileOf(author, artistId);
		return profile != null ? profile.getNickname() : author.getNickname();
	}

	// 게시글/댓글 목록을 한 번에 그릴 때 작성자마다 profileOf를 반복 조회하지 않도록 미리 맵으로 계산
	public Map<Long, String> displayNicknamesByAuthorId(Collection<User> authors, Long artistId) {
		Map<Long, String> result = new HashMap<>();
		for (User author : authors) {
			if (author != null) {
				result.putIfAbsent(author.getId(), displayNickname(author, artistId));
			}
		}
		return result;
	}

	// 화면에 프로필 카드(닉네임/소개글/아바타/배경) 그릴 때 씀
	public Map<Long, CommunityProfile> joinedProfilesByArtistId(User fan) {
		if (fan == null) return Map.of();
		Map<Long, CommunityProfile> result = new HashMap<>();
		for (CommunityMember member : communityMemberRepository.findByFanId(fan.getId())) {
			communityProfileRepository.findByCommunityMember_Id(member.getId())
					.ifPresent(profile -> result.put(member.getArtistId(), profile));
		}
		return result;
	}

	public long countMembers(Long artistId) {
		return communityMemberRepository.countByArtistId(artistId);
	}
}