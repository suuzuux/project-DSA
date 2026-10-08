package megane6.weplanet.service.payment;

import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.config.TossPaymentsProperties;
import megane6.weplanet.domain.dto.payment.TossErrorResponse;
import megane6.weplanet.domain.dto.payment.TossPaymentResponse;
import megane6.weplanet.exception.TossPaymentException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;


/** 토스 페이먼츠 API 클라이언트 ("시크릿키:" 를 Base64 로 Basic 인증). */
@Slf4j
@Component
public class TossPaymentsClient {
	
	private static final String BASE_URL = "https://api.tosspayments.com";
	
	private final RestClient restClient;
	
	public TossPaymentsClient(TossPaymentsProperties properties) {
		String encodedKey = Base64.getEncoder()
				.encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
		
		this.restClient = RestClient.builder()
				.baseUrl(BASE_URL)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedKey)
				.build();
	}
	
	// 결제 승인 (가상계좌는 계좌 발급까지, 입금은 나중)
	public TossPaymentResponse confirm(String paymentKey, String orderId, long amount) {
		try {
			return restClient.post()
					.uri("/v1/payments/confirm")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"paymentKey", paymentKey,
							"orderId", orderId,
							"amount", amount
					))
					.retrieve()
					.body(TossPaymentResponse.class);
		} catch (RestClientResponseException e) {
			// 토스가 4xx/5xx 오류 코드를 돌려준 경우
			throw toTossException(e);
		} catch (RestClientException e) {
			// 네트워크 오류 등 응답을 못 받은 경우
			log.warn("[토스] 결제 승인 통신 실패. orderId={}", orderId, e);
			throw new TossPaymentException("NETWORK_ERROR", "error.toss.network");
		}
	}
	
	/** 결제 조회 (스케줄러의 입금 확인용) */
	public TossPaymentResponse getPayment(String paymentKey) {
		try {
			return restClient.get()
					.uri("/v1/payments/{paymentKey}", paymentKey)
					.retrieve()
					.body(TossPaymentResponse.class);
		} catch (RestClientResponseException e) {
			throw toTossException(e);
		} catch (RestClientException e) {
			log.warn("[토스] 결제 조회 통신 실패", e);
			throw new TossPaymentException("NETWORK_ERROR", "error.toss.network");
		}
	}
	
	private TossPaymentException toTossException(RestClientResponseException e) {
		TossErrorResponse error = null;
		try {
			error = e.getResponseBodyAs(TossErrorResponse.class);
		} catch (RuntimeException ignored) {
			// 오류 본문이 JSON 이 아니면 기본 메시지 사용
		}
		
		String code = (error != null && error.code() != null)
				? error.code()
				: "HTTP_" + e.getStatusCode().value();
		
		String message = (error != null && error.message() != null)
				? error.message()
				: "error.toss.generic";

		log.warn("[토스] API 오류 status={} code={}", e.getStatusCode().value(), code);

		// 토스 내부 장애 메시지는 로그에만 남기고 화면엔 짧은 안내만 보여준다.
		if (e.getStatusCode().is5xxServerError() || looksLikeInternalError(message)) {
			log.warn("[토스] 결제사 내부 오류 원문: {}", message);
			// 메시지 키로 던지고 화면에서 번역한다.
			return new TossPaymentException(code, "error.toss.providerUnavailable");
		}
		return new TossPaymentException(code, message);
	}

	private static boolean looksLikeInternalError(String message) {
		return message.contains("Exception") || message.contains("###") || message.contains("SQL");
	}
}
