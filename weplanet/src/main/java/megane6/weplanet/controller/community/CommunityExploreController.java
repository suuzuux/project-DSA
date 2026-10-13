package megane6.weplanet.controller.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;
import megane6.weplanet.domain.dto.community.ArtistSearchResultView;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.community.CommunityExploreService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// 커뮤니티 검색 API (가입·편집·탈퇴는 CommunityJoinController).
@RestController
@RequiredArgsConstructor
public class CommunityExploreController {
	
	private final CommunityExploreService communityExploreService;
	private final AuthenticatedUserResolver userResolver;
	private final CommunityArtistResolver communityArtistResolver;
	
	@GetMapping("/community/search")
	public List<ArtistSearchResultView> search(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) GroupGender gender,
			@RequestParam(required = false) String nationality,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Integer memberCount,
			@RequestParam(required = false) Boolean isSolo,
			@RequestParam(required = false) LocalDate debutFrom,
			@RequestParam(required = false) LocalDate debutTo) {
		
		List<ArtistSearchResultView> results = communityExploreService.search(keyword, gender, nationality, category,
				memberCount, isSolo, debutFrom, debutTo);
		// 아티스트에게는 자기 커뮤니티에 가입 대신 "내 커뮤니티"를 표시한다.
		Long ownCommunityId = principal == null ? null
				: communityArtistResolver.ownCommunityId(userResolver.requireAuthenticated(principal));
		if (ownCommunityId == null) {
			return results;
		}
		return results.stream()
				.map(view -> view.withOwn(ownCommunityId.equals(view.artistId())))
				.toList();
	}
}