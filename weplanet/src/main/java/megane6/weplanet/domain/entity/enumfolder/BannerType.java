package megane6.weplanet.domain.entity.enumfolder;

/**
 * 메인 배너 종류. 클릭했을 때 어디로 보내는지가 다르다.
 *   COMMUNITY : 아티스트 커뮤니티 홍보 → 커뮤니티 하이라이트(/kiikii)
 *   PRODUCT   : 판매중인 굿즈 홍보     → 상품 상세(/shop/products/{id})
 */
public enum BannerType {
	COMMUNITY("커뮤니티 홍보"),
	PRODUCT("상품 홍보");

	private final String label;

	BannerType(String label) {
		this.label = label;
	}

	// 한국어 기본값. 화면에서는 getMessageKey() + MessageSource로 로케일에 맞게 보여준다.
	public String getLabel() {
		return label;
	}

	// 메시지 키 - adminBanner.type.COMMUNITY / adminBanner.type.PRODUCT
	public String getMessageKey() {
		return "adminBanner.type." + name();
	}
}
