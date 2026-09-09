package megane6.weplanet.repository.portal;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.ArtistProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {
    Optional<ArtistProfile> findByArtist(User artist);

    List<ArtistProfile> findByArtist_IdIn(Collection<Long> artistIds);

    @Query("select p.artist.id, p.logoImageUrl from ArtistProfile p "
            + "where p.artist.id in :artistIds and p.logoImageUrl is not null and p.logoImageUrl <> ''")
    List<Object[]> findLogoImageUrlsByArtistIds(@Param("artistIds") Collection<Long> artistIds);
}
