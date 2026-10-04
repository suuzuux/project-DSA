package megane6.weplanet.domain.entity.enumfolder.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 해시태그 총공 이벤트 상태
 * DB에 저장하지 않고 HashtagEvent.statusAt(지금 시각)으로 그때그때 계산한다.
 * (시간이 지나면 자동으로 예정 -> 진행중 -> 종료가 되므로 스케줄러가 필요없음)
 */
@Getter
@RequiredArgsConstructor
public enum HashtagEventStatus {
	SCHEDULED("예정"),
	ONGOING("진행 중"),
	ENDED("종료"),
	FINALIZED("집계 확정");
	
	private final String displayName;
	
	// 화면에서는 #{${status.messageKey}} 로 현재 언어 문구를 보여준다 (displayName 은 한국어 기본값)
	public String getMessageKey() {
		return "hashtag.status." + name();
	}
}
