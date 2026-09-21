package megane6.weplanet.repository;

import jakarta.persistence.LockModeType;
import megane6.weplanet.domain.entity.GoodsVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoodsVariantRepository extends JpaRepository<GoodsVariant, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT v FROM GoodsVariant v WHERE v.id = :id")
	Optional<GoodsVariant> findByIdForUpdate(@Param("id") Long id);
}
