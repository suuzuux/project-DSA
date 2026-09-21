package megane6.weplanet.domain.dto;

import java.util.List;

/**
 * 굿즈샵 상품 목록·상세용 뷰.
 * soldOut/lowStock 은 status가 아니라 Variant 재고로 판정.
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
		String officialUrl,
		int totalStock,
		int minPositiveStock,
		List<ShopVariantView> variants,
		List<String> categoryLabels
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

	public boolean soldOut() {
		return totalStock <= 0
				|| variants == null
				|| variants.isEmpty()
				|| variants.stream().allMatch(ShopVariantView::soldOut);
	}

	public boolean lowStock() {
		return minPositiveStock > 0
				&& minPositiveStock < megane6.weplanet.domain.entity.Goods.LOW_STOCK_THRESHOLD;
	}

	/** 목록·임박 표시용 (양수 최소 재고, 없으면 총재고) */
	public int stockQuantity() {
		return minPositiveStock > 0 ? minPositiveStock : totalStock;
	}
}
