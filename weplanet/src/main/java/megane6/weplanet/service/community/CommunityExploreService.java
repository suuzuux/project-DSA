package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.community.ArtistSearchResultView;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;
import megane6.weplanet.repository.community.ArtistGroupProfileRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// EXPLORE-02: 커뮤니티 검색.
// join/leave(EXPLORE-03)는 CommunityMember 엔티티가 생긴 뒤 이 서비스에 다시 추가할 예정.
@Service
@RequiredArgsConstructor
public class CommunityExploreService {
	
	private final ArtistGroupProfileRepository artistGroupProfileRepository;
	
	public List<ArtistSearchResultView> search(
			String keyword, GroupGender gender, String nationality, String category,
			Integer memberCount, Boolean isSolo, LocalDate debutFrom, LocalDate debutTo) {
		
		return artistGroupProfileRepository
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