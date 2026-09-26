package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.MembershipOrder;
import megane6.weplanet.domain.entity.ShopOrder;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;

import java.time.LocalDateTime;

public record CommercePaymentResultView(
		String kind,
		String orderNo,
		String title,
		Long amount,
		String bankName,
		String accountNumber,
		LocalDateTime dueDate,
		boolean paid,
		String backUrl,
		String backLabel
) {
	public static CommercePaymentResultView fromShop(ShopOrder order) {
		String title = order.getItems().isEmpty()
				? "굿즈 주문"
				: order.getItems().getFirst().getProductName()
				+ (order.getItems().size() > 1 ? " 외 " + (order.getItems().size() - 1) + "건" : "");
		return new CommercePaymentResultView(
				"shop",
				order.getOrderNo(),
				title,
				order.getAmount(),
				SettlementBank.displayNameOfTossCode(order.getVirtualBankCode()),
				order.getVirtualAccountNumber(),
				order.getDueDate(),
				order.getPaymentStatus() == FanProjectPaymentStatus.PAID,
				"/shop/cart",
				"장바구니로"
		);
	}

	public static CommercePaymentResultView fromMembership(MembershipOrder order) {
		String artistName = order.getArtist().getNickname() != null
				? order.getArtist().getNickname()
				: "아티스트";
		return new CommercePaymentResultView(
				"membership",
				order.getOrderNo(),
				artistName + " 멤버십 (1년)",
				order.getAmount(),
				SettlementBank.displayNameOfTossCode(order.getVirtualBankCode()),
				order.getVirtualAccountNumber(),
				order.getDueDate(),
				order.getPaymentStatus() == FanProjectPaymentStatus.PAID,
				"/community/" + order.getArtist().getId() + "/highlight",
				"커뮤니티로"
		);
	}
}
