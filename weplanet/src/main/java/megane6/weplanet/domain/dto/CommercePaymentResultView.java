package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.MembershipOrder;
import megane6.weplanet.domain.entity.ShopOrder;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;
import megane6.weplanet.i18n.Messages;

import java.time.LocalDateTime;

public record CommercePaymentResultView(
		String kind,
		String orderNo,
		String title,
		Long amount,
		String bankName,
		// 은행 이름 메시지 키 (목록에 없는 은행이면 null -> 화면은 bankName 사용)
		String bankMessageKey,
		String accountNumber,
		LocalDateTime dueDate,
		boolean paid,
		String backUrl,
		String backLabel
) {
	// 레코드라 호출하는 서비스가 Messages 를 넘겨 현재 로케일 문구로 만든다.
	public static CommercePaymentResultView fromShop(ShopOrder order, Messages messages) {
		String title;
		if (order.getItems().isEmpty()) {
			title = messages.get("shop.defaultOrderName");
		} else if (order.getItems().size() > 1) {
			title = messages.get("shop.orderNameMore",
					order.getItems().getFirst().getProductName(), order.getItems().size() - 1);
		} else {
			title = order.getItems().getFirst().getProductName();
		}
		return new CommercePaymentResultView(
				"shop",
				order.getOrderNo(),
				title,
				order.getAmount(),
				SettlementBank.displayNameOfTossCode(order.getVirtualBankCode()),
				SettlementBank.messageKeyOfTossCode(order.getVirtualBankCode()),
				order.getVirtualAccountNumber(),
				order.getDueDate(),
				order.getPaymentStatus() == FanProjectPaymentStatus.PAID,
				"/shop/cart",
				messages.get("payment.backToCart")
		);
	}

	public static CommercePaymentResultView fromMembership(MembershipOrder order, Messages messages) {
		String artistName = order.getArtist().getNickname() != null
				? order.getArtist().getNickname()
				: messages.get("shell.artistFallback");
		return new CommercePaymentResultView(
				"membership",
				order.getOrderNo(),
				messages.get("membershipCheckout.orderName", artistName),
				order.getAmount(),
				SettlementBank.displayNameOfTossCode(order.getVirtualBankCode()),
				SettlementBank.messageKeyOfTossCode(order.getVirtualBankCode()),
				order.getVirtualAccountNumber(),
				order.getDueDate(),
				order.getPaymentStatus() == FanProjectPaymentStatus.PAID,
				"/community/" + order.getArtist().getId() + "/highlight",
				messages.get("payment.backToCommunity")
		);
	}
}
