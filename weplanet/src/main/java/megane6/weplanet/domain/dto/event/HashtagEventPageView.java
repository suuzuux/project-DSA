package megane6.weplanet.domain.dto.event;

import java.util.List;
import java.util.Set;

/** 공개 이벤트 페이지 데이터 - 순위·통계, 1~3위, 내 커뮤니티 순위, 남은 시간. */
public record HashtagEventPageView(
		HashtagEventDashboard dashboard,
		List<HashtagRankingRow> topRanking,
		Set<Long> myArtistIds,
		List<HashtagRankingRow> myRows,
		String remainingText
) {
	
	public boolean isMine(Long artistId) {
		return myArtistIds.contains(artistId);
	}
}
