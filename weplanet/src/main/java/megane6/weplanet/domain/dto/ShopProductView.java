package megane6.weplanet.domain.dto;

/**
 * 굿즈샵 상품 목록·상세용 뷰.
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
		String priceSuffix,
		String thumbnailUrl,
		String description,
		String officialUrl
) {
	public String formattedPrice() {
		return "₩ " + String.format("%,d", price);
	}

	public String thumbnailPublicUrl() {
		if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
			return null;
		}
		String value = thumbnailUrl.trim();
		if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")) {
			return value;
		}
		return "/uploads/" + value;
	}
}
