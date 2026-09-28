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

	public String getLabel() {
		return label;
	}
}
