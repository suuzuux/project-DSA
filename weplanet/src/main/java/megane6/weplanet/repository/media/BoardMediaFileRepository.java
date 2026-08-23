package megane6.weplanet.repository.media;

import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardMediaFileRepository extends JpaRepository<BoardMediaFileEntity, Long> {
}
