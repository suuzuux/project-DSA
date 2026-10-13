package megane6.weplanet.controller.fan.community;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityDrawerHelper;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 햄버거 메뉴용 커뮤니티 목록 API (모든 페이지에서 shell.js 가 호출). */
@RestController
@RequiredArgsConstructor
public class SideMenuApiController {

	private final UserRepository userRepository;
	private final AuthenticatedUserResolver userResolver;
	private final CommunityJoinService communityJoinService;
	private final CommunityDrawerHelper communityDrawerHelper;
	private final PortalManagementService portalManagementService;

	@GetMapping("/api/side-menu/communities")
	public Map<String, List<ArtistCardView>> communities(@AuthenticationPrincipal AuthenticatedUser principal) {
		List<ArtistCardView> artists =
				portalManagementService.toArtistCards(userRepository.findByRole(Role.ARTIST));

		// 비로그인이면 가입 목록은 비우고 전체 커뮤니티만 내려준다.
		User viewer = null;
		Set<Long> joinedArtistIds = Collections.emptySet();
		if (principal != null) {
			viewer = userResolver.resolve(principal, 1L);
			joinedArtistIds = communityJoinService.joinedArtistIds(viewer);
		}

		return Map.of(
				"joined", communityDrawerHelper.joined(viewer, artists, joinedArtistIds),
				"others", communityDrawerHelper.otherCommunities(viewer, artists)
		);
	}
}
