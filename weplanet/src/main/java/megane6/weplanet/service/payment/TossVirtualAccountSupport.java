package megane6.weplanet.service.payment;

import megane6.weplanet.domain.dto.payment.TossPaymentResponse;
import megane6.weplanet.exception.TossPaymentException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class TossVirtualAccountSupport {

	public static final int VALID_HOURS = 24;
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private TossVirtualAccountSupport() {
	}

	public static String newOrderNo(String prefix, Long id, LocalDateTime now) {
		String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		String random = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		return prefix + "-" + id + "-" + timestamp + "-" + random;
	}

	public static LocalDateTime toKoreaTime(String isoDateTime) {
		return OffsetDateTime.parse(isoDateTime)
				.atZoneSameInstant(KOREA)
				.toLocalDateTime();
	}

	public static void requireWaitingVirtualAccount(TossPaymentResponse response) {
		TossPaymentResponse.VirtualAccount account = response.virtualAccount();
		if (!"WAITING_FOR_DEPOSIT".equals(response.status()) || account == null || account.dueDate() == null) {
			throw new TossPaymentException("UNEXPECTED_STATUS", "가상계좌 발급 결과를 확인할 수 없습니다. 다시 시도해주세요.");
		}
	}
}
