package megane6.weplanet.domain.dto.event;

import megane6.weplanet.domain.entity.enumfolder.events.HashtagEventStatus;
import megane6.weplanet.domain.entity.event.HashtagEvent;

import java.time.LocalDateTime;

// 관리자 이벤트 목록 한 줄
public record HashtagEventListItem(
		Long id,
		String title,
		LocalDateTime startAt,
		LocalDateTime endAt,
		long periodDays,
		int targetCount,
		HashtagEventStatus status
) {
	public static HashtagEventListItem of (HashtagEvent event, LocalDateTime now) {
		return new HashtagEventListItem(
				event.getId(),
				event.getTitle(),
				event.getStartAt(),
				event.getEndAt(),
				event.periodDays(),
				event.getTargets().size(),
				event.statusAt(now)
		);
	}
	
	// 시작 전(예정)일 때만 수정·삭제 가능
	public boolean editable() {
		return status == HashtagEventStatus.SCHEDULED;
	}
	
	// 상태 태그 색 (portal.css 의 tag--* 클래스). 종료 = 집계 확정을 기다리는 중이라 눈에 띄게 빨강
	public String statusTagClass() {
		return switch (status) {
			case SCHEDULED -> "tag--member";
			case ONGOING -> "tag--public";
			case ENDED -> "tag--danger";
			case FINALIZED -> "tag--private";
		};
	}
}
