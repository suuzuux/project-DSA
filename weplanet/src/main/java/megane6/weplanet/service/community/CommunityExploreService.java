package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.community.ArtistSearchResultView;
import megane6.weplanet.domain.dto.community.ArtistSearchRow;
import megane6.weplanet.domain.dto.community.CommunityProfileRequestDto;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.community.CommunityProfile;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.community.ArtistNotFoundException;
import megane6.weplanet.exception.community.CommunityNotJoinedException;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.community.CommunityProfileRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.media.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


@Service
@RequiredArgsConstructor
@Transactional
public class CommunityExploreService {
	
	private final UserRepository userRepository;
	private final CommunityMemberRepository communityMemberRepository;
	private final CommunityProfileRepository communityProfileRepository;
	private final FileStorageService fileStorageService;
	
	@Transactional(readOnly = true)
	public List<ArtistSearchResultView> search(
			String nickname, GroupGender gender, String nationality, String category,
			Integer minMembers, Integer maxMembers, LocalDate debutFrom, LocalDate debutTo,
			User fanOrNull
	) {
		List<ArtistSearchRow> rows = userRepository.searchArtists(
				blankToNull(nickname), gender, blankToNull(nationality), blankToNull(category),
				minMembers, maxMembers, debutFrom, debutTo
		);
		
		Set<Long> joinedIds = fanOrNull == null
				? Set.of()
				: Set.copyOf(communityMemberRepository.findJoinedArtistIds(fanOrNull));
		
		return rows.stream()
				.map(row -> ArtistSearchResultView.from(row, joinedIds.contains(row.id())))
				.toList();
	}
	
	// 가입(팔로우) - 이미 가입돼있으면 새로 안 만들고 그대로 반환 (멱등)
	public Map<String, Object> join(User fan, Long artistId) {
		User artist = findArtist(artistId);
		
		CommunityMember member = communityMemberRepository.findByFanAndArtist(fan, artist)
				.orElseGet(() -> communityMemberRepository.save(
						CommunityMember.builder().fan(fan).artist(artist).build()
				));
		
		boolean hasProfile = communityProfileRepository.findByCommunityMember(member).isPresent();
		
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("joined", true);
		result.put("hasProfile", hasProfile);
		if (!hasProfile) {
			attachDefaultProfile(result, fan);
		}
		return result;
	}
	
	// 커뮤니티 페이지 사이드바 "내 프로필" 위젯 + "내 프로필" 전체 페이지가 공통으로 쓰는 조회
	@Transactional(readOnly = true)
	public Map<String, Object> getMyProfile(User fan, Long artistId) {
		User artist = findArtist(artistId);
		
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("artist", ArtistCardView.from(artist));
		
		Optional<CommunityMember> memberOpt = communityMemberRepository.findByFanAndArtist(fan, artist);
		if (memberOpt.isEmpty()) {
			result.put("joined", false);
			return result;
		}
		
		CommunityMember member = memberOpt.get();
		result.put("joined", true);
		result.put("joinedAt", member.getJoinedAt());
		
		communityProfileRepository.findByCommunityMember(member).ifPresentOrElse(
				profile -> {
					result.put("hasProfile", true);
					result.put("nickname", profile.getNickname());
					result.put("bio", profile.getBio());
					result.put("avatarStoredName", profile.getAvatarStoredName());
					result.put("backgroundStoredName", profile.getBackgroundStoredName());
				},
				() -> {
					result.put("hasProfile", false);
					attachDefaultProfile(result, fan);
				}
		);
		
		return result;
	}
	
	@Transactional(readOnly = true)
	public Map<String, Object> getDefaultProfile(User fan) {
		Map<String, Object> result = new LinkedHashMap<>();
		attachDefaultProfile(result, fan);
		return result;
	}
	
	// 프로필 생성/수정 - 수정 쪽은 save() 없이 변경 감지(dirty checking)에 맡김
	public void saveProfile(User fan, Long artistId, CommunityProfileRequestDto dto) {
		User artist = findArtist(artistId);
		CommunityMember member = communityMemberRepository.findByFanAndArtist(fan, artist)
				.orElseThrow(CommunityNotJoinedException::new);
		
		communityProfileRepository.findByCommunityMember(member).ifPresentOrElse(
				profile -> {
					profile.setNickname(dto.nickname());
					profile.setBio(dto.bio());
				},
				() -> communityProfileRepository.save(
						CommunityProfile.builder()
								.communityMember(member)
								.nickname(dto.nickname())
								.bio(dto.bio())
								.build()
				)
		);
	}
	
	// 프로필 배경 이미지 업로드 - 기존 파일이 있으면 지우고 새로 저장 (파일이 계속 쌓이는 것 방지)
	public String saveBackgroundImage(User fan, Long artistId, MultipartFile file) {
		CommunityProfile profile = requireProfile(fan, artistId);
		if (profile.getBackgroundStoredName() != null) {
			fileStorageService.delete(profile.getBackgroundStoredName());
		}
		String storedName = fileStorageService.store(file);
		profile.setBackgroundStoredName(storedName);
		return storedName;
	}
	
	// 프로필 사진(아바타) 업로드 - 위와 동일한 이유로 기존 파일 삭제 후 교체
	public String saveAvatarImage(User fan, Long artistId, MultipartFile file) {
		CommunityProfile profile = requireProfile(fan, artistId);
		if (profile.getAvatarStoredName() != null) {
			fileStorageService.delete(profile.getAvatarStoredName());
		}
		String storedName = fileStorageService.store(file);
		profile.setAvatarStoredName(storedName);
		return storedName;
	}
	
	private CommunityProfile requireProfile(User fan, Long artistId) {
		User artist = findArtist(artistId);
		CommunityMember member = communityMemberRepository.findByFanAndArtist(fan, artist)
				.orElseThrow(CommunityNotJoinedException::new);
		return communityProfileRepository.findByCommunityMember(member)
				.orElseThrow(CommunityNotJoinedException::new);
	}
	
	// 다른 커뮤니티에서 이미 쓰던 닉네임/소개글이 있으면 기본값으로 붙여줌 (최초 가입자는 그냥 안 붙음)
	private void attachDefaultProfile(Map<String, Object> result, User fan) {
		communityProfileRepository.findTopByCommunityMember_FanOrderByUpdatedAtDesc(fan)
				.ifPresent(prev -> {
					result.put("defaultNickname", prev.getNickname());
					result.put("defaultBio", prev.getBio());
				});
	}
	
	private User findArtist(Long artistId) {
		return userRepository.findById(artistId)
				.filter(u -> u.getRole() == Role.ARTIST)
				.orElseThrow(ArtistNotFoundException::new);
	}
	
	private String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}