package megane6.weplanet.repository.calendar;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.calendar.ArtistSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ArtistScheduleRepository extends JpaRepository<ArtistSchedule, Long> {
	List<ArtistSchedule> findByArtistOrderByScheduleAtAsc(User artist);

	List<ArtistSchedule> findByArtistAndScheduleAtBetweenOrderByScheduleAtAsc(
			User artist, LocalDateTime start, LocalDateTime end);

	@Query("select s from ArtistSchedule s join fetch s.artist order by s.scheduleAt asc")
	List<ArtistSchedule> findAllByOrderByScheduleAtAsc();

	@Query("select s from ArtistSchedule s join fetch s.artist where s.artist.id in :artistIds order by s.scheduleAt asc")
	List<ArtistSchedule> findByArtistIdInOrderByScheduleAtAsc(@Param("artistIds") Collection<Long> artistIds);

	long countByArtistAndScheduleAtAfter(User artist, LocalDateTime scheduleAt);
}
