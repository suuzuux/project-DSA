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

    // 게시글 삭제 전 좋아요 삭제
    void deleteByPost(Post post);

    // 내 프로필 좋아요 이력 (최신순)
    List<Like> findByUserOrderByCreatedAtDesc(User user);
    List<Like> findByUserOrderByCreatedAtAsc(User user);
    
    // [배지] 이 커뮤니티 글들에 내가 누른 좋아요 수
    long countByUser_IdAndPost_Artist_Id(Long userId, Long artistId);
}
