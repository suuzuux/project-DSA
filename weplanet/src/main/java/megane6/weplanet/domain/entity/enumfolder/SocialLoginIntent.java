package megane6.weplanet.domain.entity.enumfolder;

// LOGIN: 로그인 시도. SIGNUP: 신규 가입 시도.
// LINK(AUTH-10 신설): 이미 로그인된 계정이 설정 화면에서 소셜 계정을 "연결하기"로 추가 연동하려는 시도.
public enum SocialLoginIntent {
	LOGIN, SIGNUP, LINK
}
