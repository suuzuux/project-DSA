package megane6.weplanet.domain.entity.enumfolder;

public enum EmailVerificationPurpose {
	SIGNUP,					// 회원가입 이메일 최초 인증
	FAN_PROJECT_CREATE,		// 프로젝트 등록할 때마다 진행하는 이메일 인증
	ADMIN_LOGIN,			// 최고관리자 로그인 2차 인증 (로그인 시도마다 새로 발급)
	AGENCY_ACTIVATION		// 입점 승인 후 소속사 계정 활성화 (초대 링크, 유효기간 72시간)
}