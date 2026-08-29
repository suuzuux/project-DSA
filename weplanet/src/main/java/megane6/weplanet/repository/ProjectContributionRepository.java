package megane6.weplanet.repository;

import megane6.weplanet.domain.dto.ProjectFundingSummary;
import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectContributionRepository extends JpaRepository<ProjectContribution, Long> {

    Optional<ProjectContribution> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select new megane6.weplanet.domain.dto.ProjectFundingSummary(
                contribution.project.id,
                coalesce(sum(contribution.amount - contribution.refundAmount), 0),
                count(distinct contribution.contributor.id)
            )
            from ProjectContribution contribution
            where contribution.project.id in :projectIds
              and contribution.paymentStatus = :paymentStatus
            group by contribution.project.id
            """)
    List<ProjectFundingSummary> summarizePaidByProjectIds(
            @Param("projectIds") Collection<Long> projectIds,
            @Param("paymentStatus") FanProjectPaymentStatus paymentStatus
    );
    
    @Query("""
        select contribution
        from ProjectContribution contribution
        join fetch contribution.project project
        join fetch project.artist
        where contribution.contributor.id = :contributorId
        order by contribution.createdAt desc
        """)
    List<ProjectContribution> findParticipationHistory(
            @Param("contributorId") Long contributorId
    );
}
