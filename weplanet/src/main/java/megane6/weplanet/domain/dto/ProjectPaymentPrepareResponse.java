package megane6.weplanet.domain.dto;

/** 참여하기 응답 - 토스 결제창을 여는 값 (amount 는 서버 확정 금액, validHours 는 마감 이내). */
public record ProjectPaymentPrepareResponse(
		boolean success,
		String clientKey,
		String orderId,
		String orderName,
		Long amount,
		String customerName,
		int validHours,
		String message
) {
}
