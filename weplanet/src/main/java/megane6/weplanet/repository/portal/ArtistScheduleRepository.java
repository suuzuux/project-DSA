package megane6.weplanet.repository.portal;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.ArtistSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ArtistScheduleRepository extends JpaRepository<ArtistSchedule, Long> {
    List<ArtistSchedule> findByArtistOrderByScheduleAtAsc(User artist);
    List<ArtistSchedule> findByArtistAndScheduleAtBetweenOrderByScheduleAtAsc(User artist, LocalDateTime start, LocalDateTime end);
    long countByArtistAndScheduleAtAfter(User artist, LocalDateTime scheduleAt);
}
