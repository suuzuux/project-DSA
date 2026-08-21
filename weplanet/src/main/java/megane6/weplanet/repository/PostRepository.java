package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 게시판 종류(팬/아티스트)별로 게시글 목록 조회
    List<Post> findByBoardTypeOrderByCreatedAtDesc(BoardType boardType);
}
