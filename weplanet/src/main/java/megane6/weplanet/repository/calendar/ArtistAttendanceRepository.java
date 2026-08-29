package megane6.weplanet.repository.calendar;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.calendar.ArtistAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ArtistAttendanceRepository extends JpaRepository<ArtistAttendance, Long> {

	Optional<ArtistAttendance> findByArtistAndVisitDate(User artist, LocalDate visitDate);

	List<ArtistAttendance> findByArtistAndVisitDateBetweenOrderByVisitDateAsc(
			User artist, LocalDate start, LocalDate end);

	List<ArtistAttendance> findByArtistOrderByVisitDateAsc(User artist);
}
