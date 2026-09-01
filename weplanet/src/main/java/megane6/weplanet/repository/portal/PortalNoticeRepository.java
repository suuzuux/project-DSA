package megane6.weplanet.repository.portal;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortalNoticeRepository extends JpaRepository<PortalNotice, Long> {

    @Query("""
            SELECT n FROM PortalNotice n
            WHERE n.artist = :artist
            ORDER BY n.pinned DESC, n.pinOrder ASC, n.createdAt DESC
            """)
    List<PortalNotice> findByArtistOrderByPinnedDescPinOrderAscCreatedAtDesc(@Param("artist") User artist);

    @Query("""
            SELECT n FROM PortalNotice n
            WHERE n.artist = :artist AND n.published = true
            ORDER BY n.pinned DESC, n.pinOrder ASC, n.createdAt DESC
            """)
    List<PortalNotice> findByArtistAndPublishedTrueOrderByPinnedDescPinOrderAscCreatedAtDesc(@Param("artist") User artist);

    List<PortalNotice> findByArtistAndPinnedTrueOrderByPinOrderAsc(User artist);
    Optional<PortalNotice> findByIdAndArtist(Long id, User artist);
    long countByArtist(User artist);
    long countByArtistAndPinnedTrue(User artist);
}
