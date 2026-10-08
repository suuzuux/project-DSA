package megane6.weplanet.controller.community;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.portal.PortalManagementService;
import megane6.weplanet.web.RefererRedirects;
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
					   @RequestHeader(value = "Referer", required = false) String referer,
					   HttpServletRequest request) {
		User me = userResolver.requireAuthenticated(principal);
		if (!me.canParticipateInCommunity()) {
			throw new IllegalStateException("error.community.joinFanOrArtistOnly");
		}
		if (communityArtistResolver.isArtistOf(me, artistId)) {
			throw new IllegalStateException("error.community.joinOwnCommunity");
		}
		try {
			communityJoinService.join(me, artistId, nickname, bio, avatar, background);
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			// 중복 클릭으로 동시에 가입된 경우 그대로 진행한다.
		}
		return RefererRedirects.back(referer, request, "/"); // 우리 사이트 주소일 때만 누른 화면으로 (오픈 리다이렉트 방지)
	}
	
	// 커뮤니티 프로필 편집 (remove* 는 이미지 삭제 확인 시 true).
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
							  @RequestHeader(value = "Referer", required = false) String referer,
							  HttpServletRequest request) {
		User me = userResolver.requireAuthenticated(principal);
		// 커뮤니티 아티스트는 가입 프로필이 없어 포털 프로필을 수정한다.
		if (communityArtistResolver.isArtistOf(me, artistId)) {
			portalManagementService.updateArtistCommunityProfile(me, bio, avatar, background,
					removeAvatar, removeBackground);
		} else {
			communityJoinService.editProfile(me, artistId, nickname, bio, avatar, background,
					removeAvatar, removeBackground, contentHidden);
		}
		return RefererRedirects.back(referer, request, "/"); // 우리 사이트 주소일 때만 누른 화면으로 (오픈 리다이렉트 방지)
	}
	
	@PostMapping("/community/{artistId}/leave")
	public String leave(@PathVariable Long artistId,
						@AuthenticationPrincipal AuthenticatedUser principal,
						@RequestHeader(value = "Referer", required = false) String referer,
						HttpServletRequest request) {
		User fan = userResolver.requireAuthenticated(principal);
		communityJoinService.leave(fan, artistId);
		return RefererRedirects.back(referer, request, "/"); // 우리 사이트 주소일 때만 누른 화면으로 (오픈 리다이렉트 방지)
	}
}