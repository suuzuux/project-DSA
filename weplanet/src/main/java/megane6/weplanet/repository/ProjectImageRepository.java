package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ProjectImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectImageRepository extends JpaRepository<ProjectImage, Long> {

    /**
     * 여러 프로젝트의 대표 이미지를 한 번에 조회한다.
     * 목록에서 프로젝트마다 이미지를 따로 조회하면 프로젝트 개수만큼 쿼리가 나가므로
     * (N+1 문제), id 목록을 한 번에 넘겨서 쿼리 1번으로 끝낸다.
     */
    List<ProjectImage> findByProject_IdIn(Collection<Long> projectIds);

    /**
     * 프로젝트 한 건의 대표 이미지 조회
     * project_id에 unique 제약이 걸려 있어 최대 1건이므로 Optional로 받는다.
     */
    Optional<ProjectImage> findByProject_Id(Long projectId);
}
