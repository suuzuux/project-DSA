package megane6.weplanet.repository;

import jakarta.persistence.LockModeType;
import megane6.weplanet.domain.entity.MembershipOrder;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MembershipOrderRepository extends JpaRepository<MembershipOrder, Long> {

	Optional<MembershipOrder> findByIdempotencyKey(String idempotencyKey);

	Optional<MembershipOrder> findByOrderNo(String orderNo);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from MembershipOrder o where o.orderNo = :orderNo")
	Optional<MembershipOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

	@Query("select o.orderNo from MembershipOrder o where o.paymentStatus = :status")
	List<String> findOrderNosByStatus(@Param("status") FanProjectPaymentStatus status);

	@Query("""
			select o.orderNo
			from MembershipOrder o
			where o.paymentStatus = :status
			  and o.createdAt < :before
			""")
	List<String> findOrderNosByStatusCreatedBefore(
			@Param("status") FanProjectPaymentStatus status,
			@Param("before") LocalDateTime before
	);
}
