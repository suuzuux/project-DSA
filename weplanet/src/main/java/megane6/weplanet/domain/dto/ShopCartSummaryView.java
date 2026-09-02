package megane6.weplanet.domain.dto;

import java.util.List;

/**
 * 장바구니 목록 + 합계.
 */
public record ShopCartSummaryView(
		List<ShopCartItemView> items,
		int subtotal,
		int shippingFee,
		int total
) {
	public boolean empty() {
		return items == null || items.isEmpty();
	}

	public String formattedSubtotal() {
		return formatWon(subtotal);
	}

	public String formattedShippingFee() {
		return formatWon(shippingFee);
	}

	public String formattedTotal() {
		return formatWon(total);
	}

	private static String formatWon(int amount) {
		return "₩ " + String.format("%,d", amount);
	}
}
