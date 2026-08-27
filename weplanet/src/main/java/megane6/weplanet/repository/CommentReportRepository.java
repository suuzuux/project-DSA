package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.CommentReport;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {

    // 같은 사람이 같은 댓글을 이미 신고했는지 확인
    Optional<CommentReport> findByCommentAndReporter(Comment comment, User reporter);

    // 댓글 하나 삭제할 때, 그 댓글에 달린 신고 기록도 같이 지우기 위함
    void deleteByComment(Comment comment);

    // 게시글이 삭제될 때, 그 게시글에 속한 모든 댓글의 신고 기록을 한 번에 지우기 위함
    // (Comment.post 필드를 타고 들어가는 문법 - "Comment_Post")
    void deleteByComment_Post(Post post);

    List<CommentReport> findByComment_Post_ArtistOrderByCreatedAtDesc(User artist);

    long countByComment_Post_Artist(User artist);
}
