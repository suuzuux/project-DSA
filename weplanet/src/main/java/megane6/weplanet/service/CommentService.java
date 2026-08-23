package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentReportRepository commentReportRepository;

    // 댓글 목록 조회
    public List<Comment> getComments(Post post) {
        return commentRepository.findByPostOrderByCreatedAtAsc(post);
    }

    // 댓글 단건 조회 (없으면 예외) - 댓글 신고 기능에서 사용
    public Comment getComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다. id=" + commentId));
    }

    // 댓글 작성
    public Comment createComment(Post post, User author, String content) {
        Comment comment = Comment.builder()
                .post(post)
                .author(author)
                .content(content)
                .build();

        return commentRepository.save(comment);
    }

    // 댓글 삭제 - 작성자 본인만 삭제 가능
    public void deleteComment(Long commentId, User requester) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다. id=" + commentId));

        if (!comment.getAuthor().getId().equals(requester.getId())) {
            throw new IllegalStateException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        // 댓글을 참조하는 신고 기록을 먼저 지운 뒤에 댓글을 삭제 (외래키 제약 위반 방지)
        commentReportRepository.deleteByComment(comment);
        commentRepository.delete(comment);
    }
}
