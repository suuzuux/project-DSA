package megane6.weplanet.controller.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.AuthenticatedUserResolver;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
public class CommunityJoinController {
	
	private final CommunityJoinService communityJoinService;
	private final AuthenticatedUserResolver userResolver;
	private final CommunityArtistResolver communityArtistResolver;
	private final PortalManagementService portalManagementService;

	@PostMapping("/community/{artistId}/join")
	public String join(@PathVariable Long artistId,
					   @RequestParam String nickname,
					   @RequestParam(required = false) String bio,
					   @RequestParam(required = false) MultipartFile avatar,
					   @RequestParam(required = false) MultipartFile background,
					   @AuthenticationPrincipal AuthenticatedUser principal,
					   @RequestHeader(value = "Referer", required = false) String referer) {
		User me = userResolver.requireAuthenticated(principal);
		if (!me.canParticipateInCommunity()) {
			throw new IllegalStateException("팬 또는 아티스트 계정만 커뮤니티에 가입할 수 있습니다.");
		}
		if (communityArtistResolver.isArtistOf(me, artistId)) {
			throw new IllegalStateException("본인 커뮤니티에는 가입할 수 없습니다.");
		}
		try {
			communityJoinService.join(me, artistId, nickname, bio, avatar, background);
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			// AUTH-11: 가입 버튼을 빠르게 두 번 눌러 같은 가입이 동시에 들어온 경우 - 먼저 끝난 가입이 있으므로 그대로 진행
		}
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
		User me = userResolver.requireAuthenticated(principal);
		// 이 커뮤니티의 아티스트(솔로 본인/그룹 멤버)는 가입 프로필(community_profiles)이 없으므로
		// 계정별 포털 프로필(소개/사진/배경)을 고친다. 이름과 콘텐츠 숨김은 아티스트에게 해당 없음.
		if (communityArtistResolver.isArtistOf(me, artistId)) {
			portalManagementService.updateArtistCommunityProfile(me, bio, avatar, background,
					removeAvatar, removeBackground);
		} else {
			communityJoinService.editProfile(me, artistId, nickname, bio, avatar, background,
					removeAvatar, removeBackground, contentHidden);
		}
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