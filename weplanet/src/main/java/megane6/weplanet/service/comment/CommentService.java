package megane6.weplanet.service.comment;
import megane6.weplanet.service.chat.ChatFilterService;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.comment.CommentReportRepository;
import megane6.weplanet.repository.comment.CommentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 댓글 조회·작성·삭제 서비스
@Service
@RequiredArgsConstructor
public class CommentService {

    // 답글이 남은 원댓글 삭제 시 표시 문구
    public static final String DELETED_CONTENT = "삭제된 댓글입니다";

    private final CommentRepository commentRepository;
    private final CommentReportRepository commentReportRepository;
    private final ApplicationEventPublisher eventPublisher; // [배지] 활동 이벤트 발행
    private final ChatFilterService chatFilterService; // 금칙어 검사

    // 댓글 목록 조회
    public List<Comment> getComments(Post post) {
        return commentRepository.findByPostOrderByCreatedAtAsc(post);
    }

    // 댓글 수 조회
    public long getCommentCount(Post post) {
        return commentRepository.countByPost(post);
    }

    // 댓글 단건 조회 (없으면 예외)
    public Comment getComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("error.comment.notFound"));
    }

    // 댓글 작성
    public Comment createComment(Post post, User author, String content) {
        return createComment(post, author, content, null);
    }

    // 답글 작성 (parent 가 null 이면 일반 댓글)
    public Comment createComment(Post post, User author, String content, Comment parent) {
        chatFilterService.rejectIfContainsBannedWord(content);

        if (parent != null) {
            // 다른 게시글 댓글에는 답글을 달 수 없다.
            if (!parent.getPost().getId().equals(post.getId())) {
                throw new IllegalArgumentException("error.comment.replyWrongPost");
            }
            // 답글의 답글은 막는다.
            if (parent.getParent() != null) {
                throw new IllegalStateException("error.comment.replyToReply");
            }
            if (parent.isDeleted()) {
                throw new IllegalStateException("error.comment.replyToDeleted");
            }
        }

        Comment comment = Comment.builder()
                .post(post)
                .author(author)
                .content(content)
                .parent(parent)
                .build();

        Comment saved = commentRepository.save(comment);
        // [배지] 커뮤니티 글 댓글만 배지 대상
        if (post.getAuthor() != null) {
            eventPublisher.publishEvent(new BadgeActivityEvent(
                    author.getId(), post.getArtist().getId(), BadgeActivityEvent.Activity.COMMENT_CREATED
            ));
        }
        
        return saved;
    }

    // 댓글 삭제 - 작성자 또는 관리자
    @Transactional
    public void deleteComment(Long commentId, User requester) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("error.comment.notFound"));

        boolean isAuthor = comment.getAuthor().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("error.comment.deleteNoPermission");
        }

        removeComment(comment);
    }

    /** 아티스트 커뮤니티 댓글을 에이전시·신고함에서 삭제할 때 사용 */
    @Transactional
    public void deleteCommentForArtistCommunity(Long commentId, User artist) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("error.comment.notFound"));
        if (artist == null
                || comment.getPost().getArtist() == null
                || !comment.getPost().getArtist().getId().equals(artist.getId())) {
            throw new IllegalStateException("error.comment.deleteOnlyThisCommunity");
        }
        removeComment(comment);
    }

    /** 답글이 있으면 소프트 삭제, 없으면 완전 삭제, 마지막 답글 삭제 시 소프트 삭제된 부모도 삭제한다. */
    private void removeComment(Comment comment) {
        Comment parent = comment.getParent();

        if (comment.getParent() == null && commentRepository.countByParent(comment) > 0) {
            softDelete(comment);
            return;
        }

        deleteCommentCascade(comment);

        if (parent != null && parent.isDeleted() && commentRepository.countByParent(parent) == 0) {
            deleteCommentCascade(parent);
        }
    }

    // 다른 화면에 원문이 남지 않게 내용도 비운다.
    private void softDelete(Comment comment) {
        comment.setDeletedAt(LocalDateTime.now());
        comment.setContent(DELETED_CONTENT);
        commentRepository.save(comment);
    }

    private void deleteCommentCascade(Comment comment) {
        // 신고 기록을 먼저 지운다 (FK).
        commentReportRepository.deleteByComment(comment);
        commentRepository.delete(comment);
    }

    // 댓글 수정 - 작성자 본인만
    public Comment updateComment(Long commentId, User requester, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("error.comment.notFound"));

        if (!comment.getAuthor().getId().equals(requester.getId())) {
            throw new IllegalStateException("error.comment.editNoPermission");
        }

        if (comment.isDeleted()) {
            throw new IllegalStateException("error.comment.editDeleted");
        }

        chatFilterService.rejectIfContainsBannedWord(content);

        comment.setContent(content);
        return commentRepository.save(comment);
    }
}
