package megane6.weplanet.domain.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
		String paymentKey,
		String orderId,
		String status,
		Long totalAmount,
		String secret,
		VirtualAccount virtualAccount
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record VirtualAccount(
			String accountNumber,
			String bankCode,
			String customerName,
			String dueDate	// 예 : "2026-09-22T17:30:00+09:00"
	) {
	}
}
