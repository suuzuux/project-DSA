package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    // 역할별 공개 범위는 ProjectService에서 적용한다. creator는 카드 DTO 변환에
    // 항상 필요하므로 한 번에 가져와 목록 조회 시 N+1 쿼리를 막는다.
    @EntityGraph(attributePaths = "creator")
    List<Project> findByArtistAndDeletedAtIsNull(User artist);
}
