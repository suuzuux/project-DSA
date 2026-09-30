package megane6.weplanet.domain.dto.event;

import megane6.weplanet.domain.entity.enumfolder.events.HashtagEntryStatus;
import megane6.weplanet.domain.entity.enumfolder.events.HashtagEventStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 해시태그 총공 이벤트 1개의 현황판. (관리자 모니터링 · 공개 이벤트 페이지 공통)
 * 집계 확정 전 : 지금 이 순간 계산간 숫자
 * 확정 후 : 확정 시점에 고정된 숫자
 */
public record HashtagEventDashboard(
		Long eventId,
		String title,
		LocalDateTime startAt,
		LocalDateTime endAt,
		LocalDateTime finalizedAt,
		HashtagEventStatus status,
		List<HashtagRankingRow> ranking,					// 참여율
		Map<HashtagEntryStatus, Long> excludedCounts,		// 제외 사유별 글 수 (인정 제외)
		List<HashtagDailyCount> dailyCounts
) {
	
	public long totalPostCount() {
		return ranking.stream().mapToLong(HashtagRankingRow::postCount).sum();
	}
	
	// 커뮤니티별 참여 인원의 합 (두 커뮤니티에 모두 참여한 팬은 2명으로 센다)
	public long totalParticipantCount() {
		return ranking.stream().mapToLong(HashtagRankingRow::participantCount).sum();
	}
	
	public long excludedTotal() {
		return excludedCounts.values().stream().mapToLong(Long::longValue).sum();
	}
	
	public boolean ongoing() {
		return status == HashtagEventStatus.ONGOING;
	}
	
	public boolean finalizable() {
		return status == HashtagEventStatus.ENDED;
	}
	
	public boolean finalized() {
		return status == HashtagEventStatus.FINALIZED;
	}
	
	// 상태 태그 색 (목록 화면 HashtagEventListItem.statusTagClass와 같은 규칙)
	public String statusTagClass() {
		return switch (status) {
			case SCHEDULED -> "tag--member";
			case ONGOING -> "tag--public";
			case ENDED -> "tag--danger";
			case FINALIZED -> "tag--private";
		};
	}
}
