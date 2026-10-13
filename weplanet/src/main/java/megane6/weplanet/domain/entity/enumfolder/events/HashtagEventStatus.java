package megane6.weplanet.domain.entity.enumfolder.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 총공 상태 - DB 에 저장하지 않고 시각으로 계산한다. */
@Getter
@RequiredArgsConstructor
public enum HashtagEventStatus {
	SCHEDULED("예정"),
	ONGOING("진행 중"),
	ENDED("종료"),
	FINALIZED("집계 확정");
	
	private final String displayName;
	
	// 화면 표시용 메시지 키 (displayName 은 한국어 기본값)
	public String getMessageKey() {
		return "hashtag.status." + name();
	}
}
