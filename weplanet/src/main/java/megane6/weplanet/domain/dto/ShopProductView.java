package megane6.weplanet.domain.dto;

/**
 * 굿즈샵 상품 목록·상세용 뷰 (목업 카탈로그).
 */
public record ShopProductView(
		String id,
		Long artistId,
		String artistName,
		String artistLogo,
		String title,
		int price,
		String category,
		String categoryLabel,
		boolean membershipOnly,
		String priceSuffix
) {
	public String formattedPrice() {
		return "₩ " + String.format("%,d", price);
	}
}
