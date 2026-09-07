package megane6.weplanet.domain.entity.enumfolder;

public enum AuthProvider {
	LOCAL, GOOGLE, KAKAO, LINE;
	
	public boolean emailManagedExternally() {
		return this == GOOGLE;
	}
	public boolean usesPlaceholderProfile() {
		return this == KAKAO || this == LINE;
	}
	// 카카오/LINE은 email까지 placeholder라 usesPlaceholderProfile()로 걸러지지만,
	// 구글은 email은 검증된 값을 주는 대신 name은 사용자가 임의로 바꿀 수 있는 값이라
	// realName 신뢰도만 놓고 보면 셋 다 마찬가지다. 그래서 가입 직후 실명 확인 화면
	// (/social-login/complete-profile)은 LOCAL만 빼고 모든 소셜 provider에 강제한다.
	public boolean requiresProfileCompletion() {
		return this != LOCAL;
	}
	public String placeholderRealName() {
		return switch (this) {
			case KAKAO -> "카카오사용자";
			case LINE -> "라인사용자";
			default -> null;
		};
	}
}