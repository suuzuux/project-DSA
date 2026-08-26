package megane6.weplanet.repository.community;

import megane6.weplanet.domain.entity.community.ArtistGroupProfile;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistGroupProfileRepository extends JpaRepository<ArtistGroupProfile, Long> {
	Optional<ArtistGroupProfile> findByArtist(User artist);
}