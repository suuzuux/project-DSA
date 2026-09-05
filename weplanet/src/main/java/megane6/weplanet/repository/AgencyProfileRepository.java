package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.AgencyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgencyProfileRepository extends JpaRepository<AgencyProfile, Long> {

	Optional<AgencyProfile> findByUser_Id(Long userId);
}
