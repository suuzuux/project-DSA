package megane6.weplanet.repository.portal;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.ArtistProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {
    Optional<ArtistProfile> findByArtist(User artist);
}
