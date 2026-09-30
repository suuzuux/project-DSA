package megane6.weplanet.domain.dto.event;

import java.time.LocalDate;

// 일자별 인정 글 수. barPercent는 그날 막대 높이 (가장 많은 날 = 100)
public record HashtagDailyCount(
		LocalDate date,
		long count,
		int barPercent
) {
}
