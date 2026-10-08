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

    // creator 를 함께 조회 (N+1 방지, 공개 범위는 서비스에서 적용).
    @EntityGraph(attributePaths = "creator")
    List<Project> findByArtistAndDeletedAtIsNull(User artist);
    
    List<Project> findByStatusInAndDeletedAtIsNull(
            List<FanProjectStatus> statuses
    );
    
    long countByStatusAndDeletedAtIsNull(FanProjectStatus status);
    
    @EntityGraph(attributePaths = {"artist", "creator"})
    List<Project> findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            FanProjectStatus status);
    
    // 관리자 검색 (상태·키워드·삭제 여부)
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
    
    // 삭제되지 않은 최근 프로젝트 10개 조회
    @EntityGraph(attributePaths = "creator")
    List<Project> findTop10ByArtistAndDeletedAtIsNullOrderByCreatedAtDesc(User artist);
}
