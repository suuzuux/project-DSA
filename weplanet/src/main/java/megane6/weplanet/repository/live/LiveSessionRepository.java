package megane6.weplanet.repository.live;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.LiveSessionStatus;
import megane6.weplanet.domain.entity.live.LiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

	@Query("""
			SELECT s FROM LiveSession s
			JOIN FETCH s.artist
			JOIN FETCH s.host
			WHERE s.artist.id = :artistId AND s.status = :status
			""")
	Optional<LiveSession> findFirstByArtist_IdAndStatus(
			@Param("artistId") Long artistId,
			@Param("status") LiveSessionStatus status);

	@Query("""
			SELECT s FROM LiveSession s
			JOIN FETCH s.artist
			JOIN FETCH s.host
			WHERE s.artist = :artist AND s.status = :status
			""")
	Optional<LiveSession> findFirstByArtistAndStatus(
			@Param("artist") User artist,
			@Param("status") LiveSessionStatus status);

	@Query("""
			SELECT s FROM LiveSession s
			JOIN FETCH s.artist
			WHERE s.status = :status
			  AND s.artist.id IN :artistIds
			ORDER BY s.startedAt DESC
			""")
	List<LiveSession> findLiveByArtistIds(
			@Param("status") LiveSessionStatus status,
			@Param("artistIds") Collection<Long> artistIds);
}
