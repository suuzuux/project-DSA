package megane6.weplanet.controller.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.AuthenticatedUserResolver;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
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
		// 가입은 팬만 할 수 있는 행동임(아티스트가 자기 커뮤니티에 가입되거나 관리자/소속사 계정에
		// 커뮤니티 프로필이 생기는 걸 막음). 화면에서 버튼을 숨기는 것만으로는 폼 직접 호출을
		// 막을 수 없어서 서버에서도 검증함
		if (fan.getRole() != Role.FAN) {
			throw new IllegalStateException("팬 계정만 커뮤니티에 가입할 수 있습니다.");
		}
		communityJoinService.join(fan, artistId, nickname, bio, avatar, background);
		return "redirect:" + (referer != null ? referer : "/");
	}
	
	@PostMapping("/community/{artistId}/profile/edit")
	public String editProfile(@PathVariable Long artistId,
							  @RequestParam(required = false) String nickname,
							  @RequestParam(required = false) String bio,
							  @RequestParam(required = false) MultipartFile avatar,
							  @RequestParam(required = false) MultipartFile background,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  @RequestHeader(value = "Referer", required = false) String referer) {
		User fan = userResolver.requireAuthenticated(principal);
		communityJoinService.editProfile(fan, artistId, nickname, bio, avatar, background);
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