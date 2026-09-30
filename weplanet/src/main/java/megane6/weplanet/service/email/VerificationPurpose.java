package megane6.weplanet.service.email;

// AUTH-11: 이메일 인증을 "어디에 쓰려고" 받은 것인지. 인증 결과는 용도별로 따로 저장해서,
// 한 화면에서 받은 인증을 다른 기능(예: 가입 → 이메일 변경·비밀번호 찾기)에 재사용하지 못하게 한다.
public enum VerificationPurpose {
	SIGNUP,          // 회원가입
	FIND_ID,         // 아이디 찾기
	RESET_PASSWORD,  // 비밀번호 찾기(재설정)
	EMAIL_CHANGE,    // 설정 화면의 이메일 변경
	REACTIVATE       // 휴면계정 해제
}
