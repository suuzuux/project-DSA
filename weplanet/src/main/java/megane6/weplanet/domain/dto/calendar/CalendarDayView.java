package megane6.weplanet.domain.dto.calendar;

import megane6.weplanet.domain.entity.calendar.ArtistSchedule;

import java.util.List;

public record CalendarDayView(
		int day,
		String date,
		boolean inMonth,
		boolean today,
		List<ArtistSchedule> events
) {
}
