package megane6.weplanet.domain.entity.enumfolder.calendar;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduleCategory {
	TV_BROADCAST("TV/방송", "tv_broadcast"),
	YOUTUBE("유튜브", "youtube"),
	CONCERT("콘서트", "concert"),
	RADIO("라디오", "radio"),
	AWARDS("시상식", "awards"),
	PHOTO_MAGAZINE("촬영/잡지", "photo_magazine"),
	BIRTHDAY("생일", "birthday"),
	OTHER("기타", "other");

	private final String label;
	private final String calendarType;

	public static ScheduleCategory from(String raw) {
		if (raw == null || raw.isBlank()) {
			return OTHER;
		}
		try {
			return ScheduleCategory.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return OTHER;
		}
	}
}
