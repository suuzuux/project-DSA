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

	// SETTINGS-03: 이 라벨들은 global-icons.js 캘린더 위젯이 이미 calendar.eventType.* 키로 갖고 있는
	// 것과 의미가 같아서(TV/방송, 유튜브, 콘서트 ... ), 새 키를 만들지 않고 그대로 재사용한다.
	public String getMessageKey() {
		return "calendar.eventType." + calendarType;
	}

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
