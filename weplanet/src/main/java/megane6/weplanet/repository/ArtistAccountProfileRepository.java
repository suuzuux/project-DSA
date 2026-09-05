package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Agency;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtistAccountProfileRepository extends JpaRepository<ArtistAccountProfile, Long> {

	Optional<ArtistAccountProfile> findByUser_Id(Long userId);

	List<ArtistAccountProfile> findByAgency(Agency agency);

	List<ArtistAccountProfile> findByAgency_Id(Long agencyId);
}
