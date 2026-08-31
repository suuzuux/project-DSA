package megane6.weplanet.domain.dto.calendar;

import megane6.weplanet.domain.entity.enumfolder.calendar.ScheduleCategory;
import megane6.weplanet.domain.entity.calendar.ArtistSchedule;

import java.time.format.DateTimeFormatter;
import java.util.Map;

public record ScheduleEventView(
		Long id,
		Long artistId,
		String artistName,
		String category,
		String categoryLabel,
		String type,
		String title,
		String description,
		String location,
		String ticketUrl,
		String date,
		String time
) {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	public static ScheduleEventView from(ArtistSchedule schedule) {
		ScheduleCategory category = schedule.getCategory();
		return new ScheduleEventView(
				schedule.getId(),
				schedule.getArtist().getId(),
				schedule.getArtist().getNickname(),
				category.name(),
				category.getLabel(),
				category.getCalendarType(),
				schedule.getTitle(),
				schedule.getDescription(),
				schedule.getLocation(),
				schedule.getTicketUrl(),
				schedule.getScheduleAt().format(DATE),
				schedule.getScheduleAt().format(TIME)
		);
	}

	public Map<String, String> localizedTitle() {
		return Map.of(
				"ko", title,
				"en", title,
				"ja", title,
				"zh", title,
				"fr", title,
				"es", title
		);
	}
}
