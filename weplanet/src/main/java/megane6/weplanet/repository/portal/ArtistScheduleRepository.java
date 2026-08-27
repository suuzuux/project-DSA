package megane6.weplanet.repository.portal;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.ArtistSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ArtistScheduleRepository extends JpaRepository<ArtistSchedule, Long> {
    List<ArtistSchedule> findByArtistOrderByScheduleAtAsc(User artist);
    List<ArtistSchedule> findByArtistAndScheduleAtBetweenOrderByScheduleAtAsc(User artist, LocalDateTime start, LocalDateTime end);

    @Query("select s from ArtistSchedule s join fetch s.artist order by s.scheduleAt asc")
    List<ArtistSchedule> findAllByOrderByScheduleAtAsc();

    long countByArtistAndScheduleAtAfter(User artist, LocalDateTime scheduleAt);
}
