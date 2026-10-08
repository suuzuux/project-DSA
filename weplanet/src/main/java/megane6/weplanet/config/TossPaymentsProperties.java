package megane6.weplanet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.properties 의 toss.* 설정 (clientKey 는 공개용, secretKey 는 서버 전용). */
@ConfigurationProperties(prefix = "toss")
public record TossPaymentsProperties(
		String clientKey,
		String secretKey
) {
}
