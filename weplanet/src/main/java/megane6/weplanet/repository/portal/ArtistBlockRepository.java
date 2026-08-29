package megane6.weplanet.repository.portal;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.ArtistBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtistBlockRepository extends JpaRepository<ArtistBlock, Long> {
    List<ArtistBlock> findByArtistOrderByCreatedAtDesc(User artist);
    Optional<ArtistBlock> findByArtistAndBlockedUser(User artist, User blockedUser);
    Optional<ArtistBlock> findByIdAndArtist(Long id, User artist);
    boolean existsByArtistAndBlockedUser(User artist, User blockedUser);
    long countByArtist(User artist);
}
