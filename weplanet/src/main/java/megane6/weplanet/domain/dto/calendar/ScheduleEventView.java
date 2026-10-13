package megane6.weplanet.domain.dto.calendar;

import megane6.weplanet.domain.entity.enumfolder.calendar.ScheduleCategory;
import megane6.weplanet.domain.entity.calendar.ArtistSchedule;

import java.time.LocalDateTime;
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
		String time,
		String createdAt
) {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter CREATED = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	public static ScheduleEventView from(ArtistSchedule schedule) {
		return from(schedule, schedule.getScheduleAt());
	}

	public static ScheduleEventView from(ArtistSchedule schedule, LocalDateTime occurrenceAt) {
		return from(schedule, occurrenceAt, schedule.getTitle(), schedule.getDescription());
	}

	// DB 에 한국어로 저장된 제목·설명을 현재 언어로 바꿔 넘기는 버전.
	public static ScheduleEventView from(ArtistSchedule schedule, LocalDateTime occurrenceAt,
										 String displayTitle, String displayDescription) {
		ScheduleCategory category = schedule.getCategory();
		LocalDateTime created = schedule.getCreatedAt() != null ? schedule.getCreatedAt() : occurrenceAt;
		return new ScheduleEventView(
				schedule.getId(),
				schedule.getArtist().getId(),
				schedule.getArtist().getNickname(),
				category.name(),
				category.getLabel(),
				category.getCalendarType(),
				displayTitle,
				displayDescription,
				schedule.getLocation(),
				schedule.getTicketUrl(),
				occurrenceAt.format(DATE),
				occurrenceAt.format(TIME),
				created.format(CREATED)
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
