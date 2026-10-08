package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ProjectImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectImageRepository extends JpaRepository<ProjectImage, Long> {

    /** 여러 프로젝트의 대표 이미지를 한 번에 조회 (N+1 방지). */
    List<ProjectImage> findByProject_IdIn(Collection<Long> projectIds);

    /** 프로젝트 대표 이미지 (최대 1건) */
    Optional<ProjectImage> findByProject_Id(Long projectId);
}
