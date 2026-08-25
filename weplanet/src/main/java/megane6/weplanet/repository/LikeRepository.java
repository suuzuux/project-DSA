package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Like;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    // 특정 유저가 특정 게시글에 이미 좋아요 눌렀는지 확인
    Optional<Like> findByPostAndUser(Post post, User user);

    // 게시글 삭제 시 그 게시글에 달린 좋아요를 먼저 지우기 위함
    void deleteByPost(Post post);

    // 내 프로필 "좋아요 히스토리" 탭 - 내가 좋아요 누른 게시글 전체를 최신순으로
    List<Like> findByUserOrderByCreatedAtDesc(User user);
    List<Like> findByUserOrderByCreatedAtAsc(User user);
}
