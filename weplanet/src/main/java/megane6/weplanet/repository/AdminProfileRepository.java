package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.AdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminProfileRepository extends JpaRepository<AdminProfile, Long> {

	Optional<AdminProfile> findByUser_Id(Long userId);
}
