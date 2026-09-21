package megane6.weplanet.domain.dto;

public record ShopVariantView(
		Long id,
		String optionKey,
		String optionValue,
		String label,
		int stockQuantity
) {
	/** 사이즈 선택란에 잔여 수량을 붙이는 기준 (미만) */
	public static final int OPTION_STOCK_HINT_BELOW = 10;

	public boolean soldOut() {
		return stockQuantity <= 0;
	}

	public boolean lowStock() {
		return stockQuantity > 0 && stockQuantity < megane6.weplanet.domain.entity.Goods.LOW_STOCK_THRESHOLD;
	}

	/** 사이즈 선택란: 재고 10개 미만일 때 수량 표시 */
	public boolean showOptionStockHint() {
		return stockQuantity > 0 && stockQuantity < OPTION_STOCK_HINT_BELOW;
	}

	public boolean selectable() {
		return !"DEFAULT".equals(optionKey);
	}
}
