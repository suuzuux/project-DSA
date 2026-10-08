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

// 댓글 관련 로직 (조회/작성/삭제)을 모아둔 서비스. PostService와 구조는 거의 동일함
@Service
@RequiredArgsConstructor
public class CommentService {

    // 답글이 남아 있는 원댓글을 지웠을 때 화면에 대신 보여줄 문구
    public static final String DELETED_CONTENT = "삭제된 댓글입니다";

    private final CommentRepository commentRepository;
    private final CommentReportRepository commentReportRepository;
    private final ApplicationEventPublisher eventPublisher; // [배지] 활동 알림 발행용
    private final ChatFilterService chatFilterService; // 관리자가 등록한 금칙어 검사 (작성·수정 차단)

    // 댓글 목록 조회
    public List<Comment> getComments(Post post) {
        return commentRepository.findByPostOrderByCreatedAtAsc(post);
    }

    // 댓글 개수만 조회 (게시글 목록 카드에서 "댓글 0개면 숫자 자체를 숨김" 처리용)
    public long getCommentCount(Post post) {
        return commentRepository.countByPost(post);
    }

    // 댓글 단건 조회 (없으면 예외) - 댓글 신고 기능에서 사용
    public Comment getComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("error.comment.notFound"));
    }

    // 댓글 작성
    public Comment createComment(Post post, User author, String content) {
        return createComment(post, author, content, null);
    }

    // [대댓글] 답글 작성 - parent가 null이면 일반 댓글과 완전히 동일하게 동작함
    public Comment createComment(Post post, User author, String content, Comment parent) {
        chatFilterService.rejectIfContainsBannedWord(content);

        if (parent != null) {
            // 다른 게시글의 댓글 id를 폼에 끼워 넣어도 답글이 달리지 않도록 확인
            if (!parent.getPost().getId().equals(post.getId())) {
                throw new IllegalArgumentException("error.comment.replyWrongPost");
            }
            // 답글의 답글은 막음 (1단계까지만 허용)
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
        // [배지] 커뮤니티 글에 단 댓글만 "댓글 5개" 배지 대상
        if (post.getAuthor() != null) {
            eventPublisher.publishEvent(new BadgeActivityEvent(
                    author.getId(), post.getArtist().getId(), BadgeActivityEvent.Activity.COMMENT_CREATED
            ));
        }
        
        return saved;
    }

    // 댓글 삭제 - 작성자 본인 또는 관리자만 삭제 가능 (관리자는 신고 처리를 위해 남의 댓글도 삭제할 수 있어야 함)
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

    /** 해당 아티스트 커뮤니티 댓글에 한해 에이전시/신고함에서 삭제할 때 사용 */
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

    /**
     * [대댓글] 삭제 규칙.
     * <p>
     * 원댓글 하나 지웠다고 거기 달린 답글까지 같이 날아가면 안 되므로,
     * ① 답글이 남아 있는 원댓글  -> 행은 남기고 "삭제된 댓글입니다"로 표시만 바꾼다(소프트 삭제)
     * ② 답글이 없는 댓글        -> 예전처럼 DB에서 완전히 삭제한다
     * ③ 답글을 지웠는데 부모가 이미 ①번 상태이고 남은 답글이 0개가 되면
     *    "삭제된 댓글입니다"만 덩그러니 남으므로 부모도 같이 완전히 삭제한다
     */
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

    // 내용까지 비워두는 이유: 프로필의 댓글 히스토리나 알림 등 다른 화면에도
    // 삭제한 원문이 그대로 남아 보이면 안 되기 때문
    private void softDelete(Comment comment) {
        comment.setDeletedAt(LocalDateTime.now());
        comment.setContent(DELETED_CONTENT);
        commentRepository.save(comment);
    }

    private void deleteCommentCascade(Comment comment) {
        // 댓글을 참조하는 신고 기록을 먼저 지운 뒤에 댓글을 삭제 (외래키 제약 위반 방지)
        commentReportRepository.deleteByComment(comment);
        commentRepository.delete(comment);
    }

    // 댓글 수정 - 작성자 본인만 가능. 삭제랑 똑같이 권한 체크만 하고 내용만 바꿔치기
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
