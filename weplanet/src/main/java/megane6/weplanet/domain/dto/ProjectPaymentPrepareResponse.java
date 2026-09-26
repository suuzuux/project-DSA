package megane6.weplanet.domain.dto;

/**
 * [참여하기] 응답 - 브라우저가 이 값으로 토스 결제창을 연다.
 * clientKey 	: 토스 SDK 초기화용 공개키
 * orderId		: 우리 주문번호(orderNo). 토스가 승인/웹훅 때 이 값으로 돌려준다.
 * orderName	: 결제창에 보이는 상품명
 * amount		: 서버에서 확정한 금액 (승인 단계에서 이 금액과 배교해 위변조를 막는다)
 * customerName	: 가상계좌 입금자 표시용 이름
 * validHours	: 가상계좌 입금 가능 시간 (모금 마감을 넘지 않게 서버가 계산)
 */
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
