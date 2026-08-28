package megane6.weplanet.controller.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.community.ArtistSearchResultView;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;
import megane6.weplanet.service.community.CommunityExploreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// EXPLORE-02: 커뮤니티 검색. CommunityController.java와 겹치지 않는 /community/search만 사용.
// join/leave는 EXPLORE-03에서 CommunityMember가 생긴 뒤 다시 추가.
@RestController
@RequiredArgsConstructor
public class CommunityExploreController {
	
	private final CommunityExploreService communityExploreService;
	
	@GetMapping("/community/search")
	public List<ArtistSearchResultView> search(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) GroupGender gender,
			@RequestParam(required = false) String nationality,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Integer memberCount,
			@RequestParam(required = false) Boolean isSolo,
			@RequestParam(required = false) LocalDate debutFrom,
			@RequestParam(required = false) LocalDate debutTo) {
		
		return communityExploreService.search(keyword, gender, nationality, category,
				memberCount, isSolo, debutFrom, debutTo);
	}
}