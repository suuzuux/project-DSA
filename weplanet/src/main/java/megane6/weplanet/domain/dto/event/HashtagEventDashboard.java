package megane6.weplanet.domain.dto.event;

import megane6.weplanet.domain.entity.enumfolder.events.HashtagEntryStatus;
import megane6.weplanet.domain.entity.enumfolder.events.HashtagEventStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 해시태그 총공 현황판 (확정 전은 실시간 계산, 확정 후는 고정값). */
public record HashtagEventDashboard(
		Long eventId,
		String title,
		LocalDateTime startAt,
		LocalDateTime endAt,
		LocalDateTime finalizedAt,
		HashtagEventStatus status,
		List<HashtagRankingRow> ranking,					// 참여율 순위
		Map<HashtagEntryStatus, Long> excludedCounts,		// 제외 사유별 글 수 (인정 제외)
		List<HashtagDailyCount> dailyCounts
) {
	
	public long totalPostCount() {
		return ranking.stream().mapToLong(HashtagRankingRow::postCount).sum();
	}
	
	// 커뮤니티별 참여 인원 합 (두 커뮤니티에 참여한 팬은 2명으로 셈)
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
