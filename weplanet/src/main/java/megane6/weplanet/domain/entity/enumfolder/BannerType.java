package megane6.weplanet.domain.entity.enumfolder;

/** 메인 배너 종류 - COMMUNITY(커뮤니티 이동), PRODUCT(상품 상세 이동) */
public enum BannerType {
	COMMUNITY("커뮤니티 홍보"),
	PRODUCT("상품 홍보");

	private final String label;

	BannerType(String label) {
		this.label = label;
	}

	// 한국어 기본값 (화면은 getMessageKey 로 번역)
	public String getLabel() {
		return label;
	}

	// 메시지 키 (adminBanner.type.*)
	public String getMessageKey() {
		return "adminBanner.type." + name();
	}
}
