package megane6.weplanet.repository.media;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.media.BoardMediaEntity;
import megane6.weplanet.domain.entity.media.BoardMediaLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoardMediaLikeRepository extends JpaRepository<BoardMediaLike, Long> {
	Optional<BoardMediaLike> findByBoardAndUser(BoardMediaEntity board, User user);
	void deleteByBoard(BoardMediaEntity board);
}
