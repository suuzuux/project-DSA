package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
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

/**
 * 햄버거(드로어) 메뉴용 커뮤니티 목록 API.
 * <p>
 * 원래는 각 페이지 컨트롤러가 joinedArtists/otherCommunities 를 모델에 담고
 * 템플릿이 window.__WEPLANET_*__ 로 뿌려주는 방식이었는데, 그 처리를 한 페이지(메인,
 * 커뮤니티, Shop)에서만 하고 있어서 공지사항 같은 다른 페이지에서는 메뉴가 빈 채로
 * "가입한 커뮤니티가 없어요"로 보였다. 페이지마다 같은 코드를 넣는 대신,
 * shell.js 가 필요할 때 여기서 받아가도록 창구를 하나로 모은다.
 */
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
