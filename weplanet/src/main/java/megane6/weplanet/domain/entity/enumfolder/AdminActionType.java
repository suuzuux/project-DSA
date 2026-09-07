package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminActionType {
	USER_SUSPEND("회원 계정 정지"),
	USER_REINSTATE("회원 계정 정지 해제"),
	
	ARTIST_SUSPEND("아티스트 계정 정지"),
	ARTIST_REINSTATE("아티스트 계정 정지 해제"),
	
	AGENCY_PERMISSION_APPROVE("소속사 권한 승인"),
	AGENCY_PERMISSION_REVOKE("소속사 권한 승인 취소"),
	
	PROJECT_APPROVE("프로젝트 승인"),
	PROJECT_REJECT("프로젝트 반려"),
	
	REPORT_RESOLVE("신고 처리"),
	REPORT_DISMISS("신고 기각"),
	
	SETTLEMENT_COMPLETE("정산 완료"),
	
	NOTICE_CREATE("공지 등록"),
	NOTICE_UPDATE("공지 수정"),
	NOTICE_DELETE("공지 삭제");
	
	private final String label;
}
