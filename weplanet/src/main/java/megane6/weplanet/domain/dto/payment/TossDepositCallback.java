package megane6.weplanet.domain.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 토스 가상계좌 입금 웹훅 본문 (secret 이 승인 때 저장한 값과 같아야 인정). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossDepositCallback(
		String createdAt,
		String secret,
		String status,
		String transactionKey,
		String orderId
) {
}
