package megane6.weplanet.repository;

import jakarta.persistence.LockModeType;
import megane6.weplanet.domain.dto.ProjectFundingSummary;
import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectContributionRepository extends JpaRepository<ProjectContribution, Long> {

    Optional<ProjectContribution> findByIdempotencyKey(String idempotencyKey);
    
    // 결제 승인/웹훅이 같은 주문을 동시에 처리하지 못하게 행 잠금(SELECT .. FOR UPDATE)으로 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProjectContribution c where c.orderNo = :orderNo")
    Optional<ProjectContribution> findByOrderNoForUpdate(@Param("orderNo") String orderNo);
    
    // [스케줄러] 특정 상태인 주문번호 목록
    @Query("select c.orderNo from ProjectContribution c where c.paymentStatus = :status")
    List<String> findOrderNosByStatus(@Param("status") FanProjectPaymentStatus status);
    
    // [스케줄러] 특정 상태이면서 기준 시각 이전에 만들어진 주문번호 목록 (방치된 READY 정리용)
    @Query("""
            select c.orderNo
            from ProjectContribution c
            where c.paymentStatus = :status
              and c.createdAt < :before
            """)
    List<String> findOrderNosByStatusCreatedBefore(
            @Param("status") FanProjectPaymentStatus status,
            @Param("before") LocalDateTime before
    );

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
