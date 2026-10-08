package megane6.weplanet.repository.media;

import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 파일 하나를 id 로 조회 (기본 findById 사용).
@Repository
public interface BoardMediaFileRepository extends JpaRepository<BoardMediaFileEntity, Long> {
}
