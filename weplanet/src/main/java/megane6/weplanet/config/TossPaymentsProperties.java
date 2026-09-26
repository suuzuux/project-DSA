package megane6.weplanet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.properties 의 toss.* 값을 묶어서 받는 설정 클래스.
 * toss.client-key -> clientKey, toss.secret-key -> secretKey 로 자동 매핑된다.
 * clientKey : 브라우저(결제창 SDK)에 내려줘도 되는 공개 키
 * secretKey : 서버에서 토스 API 호출할 때만 쓰는 비밀 키 (화면/로그에 절대 노출 X)
 */
@ConfigurationProperties(prefix = "toss")
public record TossPaymentsProperties(
		String clientKey,
		String secretKey
) {
}
