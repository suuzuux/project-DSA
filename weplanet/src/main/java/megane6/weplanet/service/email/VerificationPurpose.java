package megane6.weplanet.service.email;

// 인증 용도 (용도별로 저장해 다른 기능에 재사용하지 못하게 함).
public enum VerificationPurpose {
	SIGNUP,          // 회원가입
	FIND_ID,         // 아이디 찾기
	RESET_PASSWORD,  // 비밀번호 찾기(재설정)
	EMAIL_CHANGE,    // 설정 화면의 이메일 변경
	REACTIVATE       // 휴면계정 해제
}
