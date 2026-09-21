package megane6.weplanet.domain.dto;

public record ShopVariantView(
		Long id,
		String optionKey,
		String optionValue,
		String label,
		int stockQuantity
) {
	public boolean soldOut() {
		return stockQuantity <= 0;
	}

	public boolean lowStock() {
		return stockQuantity > 0 && stockQuantity < megane6.weplanet.domain.entity.Goods.LOW_STOCK_THRESHOLD;
	}

	public boolean selectable() {
		return !"DEFAULT".equals(optionKey);
	}
}
