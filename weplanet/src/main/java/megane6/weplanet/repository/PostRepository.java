package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 게시판 종류(팬/아티스트)별로 게시글 목록 조회 - 최신순
    List<Post> findByBoardTypeOrderByCreatedAtDesc(BoardType boardType);

    // 게시판 종류별로 게시글 목록 조회 - 인기순(좋아요 많은 순, 동률이면 최신순)
    List<Post> findByBoardTypeOrderByLikeCountDescCreatedAtDesc(BoardType boardType);

    // 메인 페이지 "최신 인기 포스트" 위젯용 - 게시판 종류 구분 없이 전체에서 인기순 상위 4개
    List<Post> findTop4ByOrderByLikeCountDescCreatedAtDesc();
}
