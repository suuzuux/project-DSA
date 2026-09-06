package megane6.weplanet.repository;

import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    // 역할별 공개 범위는 ProjectService에서 적용한다. creator는 카드 DTO 변환에
    // 항상 필요하므로 한 번에 가져와 목록 조회 시 N+1 쿼리를 막는다.
    @EntityGraph(attributePaths = "creator")
    List<Project> findByArtistAndDeletedAtIsNull(User artist);
    
    List<Project> findByStatusInAndDeletedAtIsNull(
            List<FanProjectStatus> statuses
    );
    
    long countByStatusAndDeletedAtIsNull(FanProjectStatus status);
    
    @EntityGraph(attributePaths = {"artist", "creator"})
    List<Project> findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            FanProjectStatus status);
    
    // 관리자 화면에서 검색 가능 (상태별, 키워드, 삭제된 프로젝트, 최신 프로젝트)
    @EntityGraph(attributePaths = {
            "artist",
            "creator",
            "reviewedBy"
    })
    @Query("""
        select project
        from Project project
        where project.deletedAt is null
          and (:status is null or project.status = :status)
          and (
              :keyword is null
              or lower(project.title)
                    like lower(concat('%', :keyword, '%'))
              or lower(project.artist.nickname)
                    like lower(concat('%', :keyword, '%'))
              or lower(project.creator.nickname)
                    like lower(concat('%', :keyword, '%'))
          )
        order by project.createdAt desc
        """)
    List<Project> searchForAdmin(@Param("status") FanProjectStatus status,
                                 @Param("keyword") String keyword);
    
    @Query("""
        select new megane6.weplanet.domain.dto.ArtistCount(
            project.artist.id,
            count(project)
        )
        from Project project
        where project.deletedAt is null
          and project.artist.id in :artistIds
          and project.status in :statuses
        group by project.artist.id
        """)
    List<ArtistCount> countProjectsByArtistIdsAndStatuses(
            @Param("artistIds") Collection<Long> artistIds,
            @Param("statuses") Collection<FanProjectStatus> statuses
    );
}
