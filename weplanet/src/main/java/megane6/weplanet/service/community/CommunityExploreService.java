package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.community.ArtistSearchResultView;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;
import megane6.weplanet.repository.ArtistGroupRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// 커뮤니티 검색 (가입·편집·탈퇴는 CommunityJoinService)
@Service
@RequiredArgsConstructor
public class CommunityExploreService {
	
	private final ArtistGroupRepository artistGroupRepository;

	public List<ArtistSearchResultView> search(
			String keyword, GroupGender gender, String nationality, String category,
			Integer memberCount, Boolean isSolo, LocalDate debutFrom, LocalDate debutTo) {

		return artistGroupRepository
				.search(blankToNull(keyword), gender, blankToNull(nationality), blankToNull(category),
						memberCount, isSolo, debutFrom, debutTo)
				.stream()
				.map(ArtistSearchResultView::of)
				.toList();
	}
	
	private String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}