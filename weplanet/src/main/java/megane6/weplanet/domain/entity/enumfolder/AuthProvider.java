package megane6.weplanet.domain.entity.enumfolder;

// 계정에 연동된 소셜 provider (연동이 없으면 null).
public enum AuthProvider {
	GOOGLE, KAKAO, LINE;

	// 카카오·LINE 가입 시 실명 대신 넣는 임시 표시값
	public String placeholderRealName() {
		return switch (this) {
			case KAKAO -> "카카오사용자";
			case LINE -> "라인사용자";
			default -> null;
		};
	}
}
