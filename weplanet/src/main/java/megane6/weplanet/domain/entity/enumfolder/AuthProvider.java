package megane6.weplanet.domain.entity.enumfolder;

// AUTH-10: LOCAL 값을 없앴다. 이제 이 enum은 "계정에 연동된 소셜 provider"만 표현한다.
// 로컬 전용 계정(소셜 연동이 없는 계정)은 User.provider가 null이다 - provider == null 인지로 판단할 것.
public enum AuthProvider {
	GOOGLE, KAKAO, LINE;

	// 카카오/LINE은 회원가입 시점에 실명을 안 주므로, User.realName에 채워 넣을 임시 표시값.
	public String placeholderRealName() {
		return switch (this) {
			case KAKAO -> "카카오사용자";
			case LINE -> "라인사용자";
			default -> null;
		};
	}
}
