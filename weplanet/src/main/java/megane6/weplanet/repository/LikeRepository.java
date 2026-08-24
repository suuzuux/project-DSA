package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Like;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    // 특정 유저가 특정 게시글에 이미 좋아요 눌렀는지 확인
    Optional<Like> findByPostAndUser(Post post, User user);
}
