package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Agency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgencyRepository extends JpaRepository<Agency, Long> {

	Optional<Agency> findByName(String name);
}
