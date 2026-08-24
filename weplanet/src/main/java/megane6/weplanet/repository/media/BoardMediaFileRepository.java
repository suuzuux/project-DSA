package megane6.weplanet.repository.media;

import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 화면에서 이미지/영상을 뿌릴 때 파일 하나를 id 로 꺼내려고 사용.
// 기본 findById 만 있으면 되므로 추가 메서드는 없다.
@Repository
public interface BoardMediaFileRepository extends JpaRepository<BoardMediaFileEntity, Long> {
}
