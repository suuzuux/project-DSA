package megane6.weplanet.domain.dto;

/**
 * 장바구니 한 줄 표시용 뷰.
 */
public record ShopCartItemView(
		Long itemId,
		ShopProductView product,
		int quantity,
		int unitPrice,
		int lineTotal
) {
	public String formattedUnitPrice() {
		return formatWon(unitPrice);
	}

	public String formattedLineTotal() {
		return formatWon(lineTotal);
	}

	private static String formatWon(int amount) {
		return "₩ " + String.format("%,d", amount);
	}
}
