package megane6.weplanet.repository.media;

import megane6.weplanet.domain.entity.media.BoardMediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoardMediaRepository extends JpaRepository<BoardMediaEntity, Long> {

    // 커뮤니티별 목록 (삭제 안 된 것만, 최신순)
    List<BoardMediaEntity> findByGroupIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long groupId);

    // 단건 조회 (삭제 안 된 것만)
    Optional<BoardMediaEntity> findByIdAndDeletedAtIsNull(Long id);
}
