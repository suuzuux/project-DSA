package megane6.weplanet.domain.dto.event;

import java.util.List;
import java.util.Set;

/**
 * 팬 공개 이벤트 페이지(/events/hashtag)에 필요한 것 한 묶음.
 *
 * @param dashboard     순위·통계 (관리자 모니터링과 같은 계산)
 * @param topRanking    1~3위
 * @param myArtistIds   로그인한 사람이 가입한 커뮤니티 id (비로그인이면 비어 있음)
 * @param myRows        그중 이번 총공에 참여한 커뮤니티의 순위 줄들 (여러 개일 수 있음)
 * @param remainingText 진행 중이면 종료까지, 예정이면 시작까지 남은 시간. 끝났으면 null
 */
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
