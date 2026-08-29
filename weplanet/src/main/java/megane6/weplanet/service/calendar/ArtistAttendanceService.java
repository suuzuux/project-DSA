package megane6.weplanet.service.calendar;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.calendar.ArtistAttendance;
import megane6.weplanet.repository.calendar.ArtistAttendanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class ArtistAttendanceService {

	private static final String[] PAW_COLORS = {
			"#e11d48", "#c45c26", "#0f6b6b", "#1d6fd8",
			"#7c5cff", "#be185d", "#b45309", "#059669",
			"#db2777", "#2563eb", "#7c3aed", "#ea580c"
	};

	private final ArtistAttendanceRepository artistAttendanceRepository;

	public void recordVisitIfArtist(User user) {
		if (user == null || user.getRole() != Role.ARTIST) {
			return;
		}
		LocalDate today = LocalDate.now();
		artistAttendanceRepository.findByArtistAndVisitDate(user, today)
				.orElseGet(() -> artistAttendanceRepository.save(
						ArtistAttendance.create(user, today, randomPawColor())
				));
	}

	@Transactional(readOnly = true)
	public Map<String, String> getPawColorsForMonth(User artist, YearMonth month) {
		if (artist == null) {
			return Map.of();
		}
		LocalDate start = month.atDay(1);
		LocalDate end = month.atEndOfMonth();
		List<ArtistAttendance> rows = artistAttendanceRepository
				.findByArtistAndVisitDateBetweenOrderByVisitDateAsc(artist, start, end);
		Map<String, String> colors = new LinkedHashMap<>();
		for (ArtistAttendance row : rows) {
			colors.put(row.getVisitDate().toString(), row.getPawColor());
		}
		return colors;
	}

	@Transactional(readOnly = true)
	public Map<String, String> getAllPawColors(User artist) {
		if (artist == null) {
			return Map.of();
		}
		Map<String, String> colors = new LinkedHashMap<>();
		for (ArtistAttendance row : artistAttendanceRepository.findByArtistOrderByVisitDateAsc(artist)) {
			colors.put(row.getVisitDate().toString(), row.getPawColor());
		}
		return colors;
	}

	private static String randomPawColor() {
		return PAW_COLORS[ThreadLocalRandom.current().nextInt(PAW_COLORS.length)];
	}
}
