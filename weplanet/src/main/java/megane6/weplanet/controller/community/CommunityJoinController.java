package megane6.weplanet.controller.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.AuthenticatedUserResolver;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityJoinService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
public class CommunityJoinController {
	
	private final CommunityJoinService communityJoinService;
	private final AuthenticatedUserResolver userResolver;
	
	@PostMapping("/community/{artistId}/join")
	public String join(@PathVariable Long artistId,
					   @RequestParam String nickname,
					   @RequestParam(required = false) String bio,
					   @RequestParam(required = false) MultipartFile avatar,
					   @RequestParam(required = false) MultipartFile background,
					   @AuthenticationPrincipal AuthenticatedUser principal,
					   @RequestHeader(value = "Referer", required = false) String referer) {
		User fan = userResolver.requireAuthenticated(principal);
		communityJoinService.join(fan, artistId, nickname, bio, avatar, background);
		return "redirect:" + (referer != null ? referer : "/");
	}
	
	// PROFILE-01: 프로필 편집
	// removeAvatar/removeBackground - 화면의 "이미지 삭제하기"를 확인했을 때 true로 넘어온다.
	// 값이 아예 안 오는 경우(다른 화면에서 호출)도 있어 기본값 false로 둔다.
	@PostMapping("/community/{artistId}/profile/edit")
	public String editProfile(@PathVariable Long artistId,
							  @RequestParam(required = false) String nickname,
							  @RequestParam(required = false) String bio,
							  @RequestParam(required = false) MultipartFile avatar,
							  @RequestParam(required = false) MultipartFile background,
							  @RequestParam(defaultValue = "false") boolean removeAvatar,
							  @RequestParam(defaultValue = "false") boolean removeBackground,
							  @RequestParam(defaultValue = "false") boolean contentHidden,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  @RequestHeader(value = "Referer", required = false) String referer) {
		User fan = userResolver.requireAuthenticated(principal);
		communityJoinService.editProfile(fan, artistId, nickname, bio, avatar, background,
				removeAvatar, removeBackground, contentHidden);
		return "redirect:" + (referer != null ? referer : "/");
	}
	
	@PostMapping("/community/{artistId}/leave")
	public String leave(@PathVariable Long artistId,
						@AuthenticationPrincipal AuthenticatedUser principal,
						@RequestHeader(value = "Referer", required = false) String referer) {
		User fan = userResolver.requireAuthenticated(principal);
		communityJoinService.leave(fan, artistId);
		return "redirect:" + (referer != null ? referer : "/");
	}
}