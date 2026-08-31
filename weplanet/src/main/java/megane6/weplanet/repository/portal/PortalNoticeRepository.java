package megane6.weplanet.repository.portal;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortalNoticeRepository extends JpaRepository<PortalNotice, Long> {
    List<PortalNotice> findByArtistOrderByCreatedAtDesc(User artist);
    List<PortalNotice> findByArtistAndPublishedTrueOrderByCreatedAtDesc(User artist);
    Optional<PortalNotice> findByIdAndArtist(Long id, User artist);
    long countByArtist(User artist);
}
