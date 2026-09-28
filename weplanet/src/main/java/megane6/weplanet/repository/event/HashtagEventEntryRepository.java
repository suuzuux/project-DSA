package megane6.weplanet.repository.event;

import megane6.weplanet.domain.entity.event.HashtagEventEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HashtagEventEntryRepository extends JpaRepository<HashtagEventEntry, Long> {
}