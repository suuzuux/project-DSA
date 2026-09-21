package megane6.weplanet.domain.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 토스 API 실패 응답 형식 : {"code":"...","message":"..."}
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossErrorResponse(
		String code,
		String message
) {
}
