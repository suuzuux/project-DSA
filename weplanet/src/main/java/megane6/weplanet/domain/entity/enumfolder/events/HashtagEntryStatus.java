package megane6.weplanet.domain.entity.enumfolder.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 해시태그가 들어간 글이 집계에 인정됐는지, 아니면 왜 제외됐는지.
 * 제외된 글도 기록해두면 관리자 모니터링에서 "제외된 글 N건(사유별)"을 보여줄 수 있다.
 */
@Getter
@RequiredArgsConstructor
public enum HashtagEntryStatus {
	COUNTED("인정"),
	DAILY_LIMIT("1인 1일 3건 초과"),
	HIDDEN_FROM_ARTIST("Hide from Artists 글"),
	NOT_MEMBER("커뮤니티 미가입");
	
	private final String displayName;
	
	// 화면에서는 #{${status.messageKey}} 로 현재 언어 문구를 보여준다 (displayName 은 한국어 기본값)
	public String getMessageKey() {
		return "hashtag.entryStatus." + name();
	}
	
	public boolean isCounted() {
		return this == COUNTED;
	}
}
