package megane6.weplanet.domain.entity.enumfolder.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 해시태그 글의 집계 인정 여부와 제외 사유. */
@Getter
@RequiredArgsConstructor
public enum HashtagEntryStatus {
	COUNTED("인정"),
	DAILY_LIMIT("1인 1일 3건 초과"),
	HIDDEN_FROM_ARTIST("Hide from Artists 글"),
	NOT_MEMBER("커뮤니티 미가입");
	
	private final String displayName;
	
	// 화면 표시용 메시지 키 (displayName 은 한국어 기본값)
	public String getMessageKey() {
		return "hashtag.entryStatus." + name();
	}
	
	public boolean isCounted() {
		return this == COUNTED;
	}
}
