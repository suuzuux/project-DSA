package megane6.weplanet.domain.entity.enumfolder;

public enum AuthProvider {
	LOCAL, GOOGLE, KAKAO, LINE;

	// 이 provider가 검증된 이메일을 그대로 내려줘서, 우리 쪽에서 이메일을 마음대로 바꾸면
	// "이메일로 기존 계정 찾기/연동" 로직이 깨지는 경우인지 여부.
	// 구글만 해당(OIDC로 검증된 실제 이메일을 받음). 카카오/LINE은 애초에 실제 이메일을 안 주고
	// placeholder만 생성하므로, LOCAL 계정처럼 사용자가 직접 실제 이메일을 입력해서 바꿀 수 있어야 한다.
	public boolean emailManagedExternally() {
		return this == GOOGLE;
	}

	// 이 provider로 신규가입했을 때 realName/email이 진짜 값이 아니라 placeholder로 채워지는지 여부.
	// 카카오는 닉네임만 주고(email 동의항목 신청 안 함), LINE도 같은 패턴으로 갈 예정이라 true.
	// 이 값이 true인 provider로 "신규가입"한 직후엔 OAuth2LoginSuccessHandler가
	// 가입 직후 한 번만 실명/이메일을 입력받는 화면(/social-login/complete-profile)으로 보낸다.
	public boolean usesPlaceholderProfile() {
		return this == KAKAO || this == LINE;
	}

	// OAuth2LoginSuccessHandler가 이 provider로 신규가입할 때 만들어주는 placeholder 이메일의 도메인.
	// User.hasPlaceholderSocialProfile()에서 "아직 진짜 이메일로 안 바꾼 회원인지" 판별할 때 쓴다.
	// DB 컬럼을 새로 추가하지 않고, 가입 시 심어둔 이메일 형식 자체로 판별하는 방식.
	public String placeholderEmailDomain() {
		if (this == KAKAO) {
			return "kakao.weplanet.local";
		}
		if (this == LINE) {
			return "line.weplanet.local";
		}
		return null;
	}
}