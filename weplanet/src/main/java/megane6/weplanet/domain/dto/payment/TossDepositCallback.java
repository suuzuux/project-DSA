package megane6.weplanet.domain.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 가상계좌 입금 웹혹(DEPOSIT_CALLBACK) 본문.
 * status : DONE(입금 완료), WAITING_FOR_DEPOSIT(입금 취소 등으로 되돌아감), CANCELED(결제 취소)
 * secret : 결제 승인 때 받아서 저장해둔 값과 같아야 진짜 토스 요청으로 인정한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossDepositCallback(
		String createdAt,
		String secret,
		String status,
		String transactionKey,
		String orderId
) {
}
