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


/**
 * 토스 페이먼츠 서버 API 호출 전담 클래스
 * 인증방식 : "시크릿키:"를 Base64로 인코딩해서 Authorization: Basic 헤더에 넣는다
 * (시크릿 키 뒤에 콜론(:)을 꼭 붙여야 함 - 비밀번호 없는 Basic 인증 형식)
 */
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
	
	// 결제 승인. 가상계좌는 여기서 "계좌 발급"까지만 되고, 돈은 나중에 입금된다
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
			// 토스가 4xx/5xx로 에러코드를 돌려준 경우
			throw toTossException(e);
		} catch (RestClientException e) {
			// 네트워크 오류 등 응답 자체를 못 받은 경우
			log.warn("[토스] 결제 승인 통신 실패. orderId={}", orderId, e);
			throw new TossPaymentException("NETWORK_ERROR", "결제사와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.");
		}
	}
	
	/**
	 * 결제 조회. 스케줄러가 입금 대기 주문의 실제 상태(입금됐는지)를 확인할 때 쓴다.
	 */
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
			throw new TossPaymentException("NETWORK_ERROR", "결제사와 통신하지 못했습니다.");
		}
	}
	
	private TossPaymentException toTossException(RestClientResponseException e) {
		TossErrorResponse error = null;
		try {
			error = e.getResponseBodyAs(TossErrorResponse.class);
		} catch (RuntimeException ignored) {
			// 에러 본문이 JSON이 아니면 아래 기본 메시지 사용
		}
		
		String code = (error != null && error.code() != null)
				? error.code()
				: "HTTP_" + e.getStatusCode().value();
		
		String message = (error != null && error.message() != null)
				? error.message()
				: "결제 처리 중 오류가 발생했습니다.";
		
		log.warn("[토스] API 오류 status={} code={}", e.getStatusCode().value(), code);
		return new TossPaymentException(code, message);
	}
}
