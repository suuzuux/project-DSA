package megane6.weplanet.domain.dto.calendar;

import java.util.List;

public record CalendarDayView(
		int day,
		String date,
		boolean inMonth,
		boolean today,
		List<ScheduleEventView> events
) {
}
