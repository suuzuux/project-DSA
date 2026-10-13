package megane6.weplanet.repository.fan;

import megane6.weplanet.domain.entity.Bookmark;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 좋아요와 같은 구조
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    // 북마크 여부 확인 (토글용)
    Optional<Bookmark> findByPostAndUser(Post post, User user);

    // 게시글 삭제 전 북마크 삭제
    void deleteByPost(Post post);

    // 내 프로필 북마크 이력 (최신순)
    List<Bookmark> findByUserOrderByCreatedAtDesc(User user);
}
