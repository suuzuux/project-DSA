package megane6.weplanet.repository.live;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.live.LiveComment;
import megane6.weplanet.domain.entity.live.LiveCommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LiveCommentReportRepository extends JpaRepository<LiveCommentReport, Long> {

	Optional<LiveCommentReport> findByCommentAndReporter(LiveComment comment, User reporter);

	void deleteByComment(LiveComment comment);

	@Query("""
			SELECT r.comment.id FROM LiveCommentReport r
			WHERE r.reporter = :reporter AND r.comment.id IN :commentIds
			""")
	List<Long> findReportedCommentIds(@Param("reporter") User reporter,
									  @Param("commentIds") Collection<Long> commentIds);

	@Query("""
			SELECT r FROM LiveCommentReport r
			JOIN FETCH r.comment c
			JOIN FETCH c.author
			JOIN FETCH c.session s
			JOIN FETCH r.reporter
			WHERE s.artist = :artist
			ORDER BY r.createdAt DESC
			""")
	List<LiveCommentReport> findByArtistOrderByCreatedAtDesc(@Param("artist") User artist);

	@Query("""
			SELECT COUNT(r) FROM LiveCommentReport r
			WHERE r.comment.session.artist = :artist
			""")
	long countByArtist(@Param("artist") User artist);
}
