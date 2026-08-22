package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 특정 게시글의 댓글을 작성일 오름차순으로 조회
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);

    // 게시글 삭제 시 그 게시글에 달린 댓글을 먼저 지우기 위함
    void deleteByPost(Post post);
}
