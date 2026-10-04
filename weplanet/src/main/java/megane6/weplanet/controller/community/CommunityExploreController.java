package megane6.weplanet.controller.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.AuthenticatedUserResolver;
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

// EXPLORE-02: 커뮤니티 검색. CommunityController.java와 겹치지 않는 /community/search만 사용.
// 커뮤니티 가입/프로필 편집/탈퇴는 CommunityJoinController(EXPLORE-03)가 맡는다.
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
		// AUTH-11: 아티스트(그룹 멤버 포함)에게는 자기 커뮤니티에 "가입" 버튼이 나오지 않도록 표시해 준다
		// (예전에는 버튼이 보이고, 누르면 "본인 커뮤니티에는 가입할 수 없습니다" 오류가 났다)
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