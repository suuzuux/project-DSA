package megane6.weplanet.controller.community;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.AuthenticatedUserResolver;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.community.ArtistSearchResultView;
import megane6.weplanet.domain.dto.community.CommunityProfileRequestDto;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.community.LoginRequiredException;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityExploreService;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// CommunityController(팀원 작성, 완성됨)는 손대지 않고 별도 컨트롤러로 분리.
// web5의 ReplyController처럼 REST 방식으로 두고, 로직은 CommunityExploreService에 위임.
@RestController
@RequiredArgsConstructor
public class CommunityExploreController {
	
	private final CommunityExploreService communityExploreService;
	private final AuthenticatedUserResolver userResolver;
	private final UserRepository userRepository;
	
	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
	}
	
	@GetMapping("/community/search")
	public List<ArtistSearchResultView> search(
			@RequestParam(required = false) String nickname,
			@RequestParam(required = false) GroupGender gender,
			@RequestParam(required = false) String nationality,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Integer minMembers,
			@RequestParam(required = false) Integer maxMembers,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debutFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debutTo,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		User fan = principal == null ? null : userResolver.resolve(principal, 1L);
		return communityExploreService.search(nickname, gender, nationality, category, minMembers, maxMembers, debutFrom, debutTo, fan);
	}
	
	@PostMapping("/community/{artistId}/join")
	public Map<String, Object> join(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) throw new LoginRequiredException();
		User fan = userResolver.resolve(principal, 1L);
		return communityExploreService.join(fan, artistId);
	}
	
	@PostMapping("/community/{artistId}/profile")
	public Map<String, Object> saveProfile(
			@PathVariable Long artistId,
			@Valid @RequestBody CommunityProfileRequestDto dto,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		if (principal == null) throw new LoginRequiredException();
		User fan = userResolver.resolve(principal, 1L);
		communityExploreService.saveProfile(fan, artistId, dto);
		return Map.of("ok", true);
	}
	
	// 커뮤니티 페이지 사이드바 "내 프로필" 위젯이 호출하는 조회 전용 엔드포인트
	@GetMapping("/community/{artistId}/profile")
	public Map<String, Object> myProfile(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) throw new LoginRequiredException();
		User fan = userResolver.resolve(principal, 1L);
		return communityExploreService.getMyProfile(fan, artistId);
	}
	
	// "가입하기" 클릭 시 모달을 열면서 바로 부르는, 가입 여부와 무관한 기본값 조회용 엔드포인트
	@GetMapping("/community/my-default-profile")
	public Map<String, Object> myDefaultProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) throw new LoginRequiredException();
		User fan = userResolver.resolve(principal, 1L);
		return communityExploreService.getDefaultProfile(fan);
	}
	
	// "내 프로필" 위젯 클릭 시 이동하는 전체 페이지
	@GetMapping("/community/{artistId}/my-profile")
	public ModelAndView myProfilePage(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return new ModelAndView("redirect:/login");
		}
		User fan = userResolver.resolve(principal, 1L);
		Map<String, Object> data = communityExploreService.getMyProfile(fan, artistId);
		if (!Boolean.TRUE.equals(data.get("joined"))) {
			return new ModelAndView("redirect:/community/" + artistId + "/highlight");
		}
		
		ModelAndView mav = new ModelAndView("community/my-profile");
		mav.addAllObjects(data);
		// 헤더/드로어가 공통으로 쓰는 전체 아티스트 목록 - CommunityController.populateArtistModel()과 동일하게 맞춤
		mav.addObject("artists", userRepository.findByRole(Role.ARTIST).stream().map(ArtistCardView::from).toList());
		return mav;
	}
	
	// 프로필 배경 이미지 업로드
	@PostMapping("/community/{artistId}/profile/background")
	public Map<String, Object> uploadBackground(
			@PathVariable Long artistId,
			@RequestParam("background") MultipartFile background,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		if (principal == null) throw new LoginRequiredException();
		User fan = userResolver.resolve(principal, 1L);
		String storedName = communityExploreService.saveBackgroundImage(fan, artistId, background);
		return Map.of("storedName", storedName);
	}
	
	// 프로필 사진(아바타) 업로드
	@PostMapping("/community/{artistId}/profile/avatar")
	public Map<String, Object> uploadAvatar(
			@PathVariable Long artistId,
			@RequestParam("avatar") MultipartFile avatar,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		if (principal == null) throw new LoginRequiredException();
		User fan = userResolver.resolve(principal, 1L);
		String storedName = communityExploreService.saveAvatarImage(fan, artistId, avatar);
		return Map.of("storedName", storedName);
	}
}