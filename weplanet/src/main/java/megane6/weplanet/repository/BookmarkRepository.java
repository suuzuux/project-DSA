package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Bookmark;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 좋아요(LikeRepository)와 완전히 같은 구조
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    // 특정 유저가 특정 게시글을 이미 북마크했는지 확인 (토글 처리에 사용)
    Optional<Bookmark> findByPostAndUser(Post post, User user);

    // 게시글 삭제 시 그 게시글에 달린 북마크 기록도 같이 지우기 위함
    void deleteByPost(Post post);
}
