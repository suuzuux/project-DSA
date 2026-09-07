package megane6.weplanet.repository.live;

import megane6.weplanet.domain.entity.live.LiveComment;
import megane6.weplanet.domain.entity.live.LiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LiveCommentRepository extends JpaRepository<LiveComment, Long> {

	@Query("""
			SELECT c FROM LiveComment c
			JOIN FETCH c.author
			WHERE c.session = :session
			ORDER BY c.createdAt ASC
			""")
	List<LiveComment> findBySessionOrderByCreatedAtAsc(@Param("session") LiveSession session);
}
