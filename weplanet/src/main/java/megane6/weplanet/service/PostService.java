package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Like;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.repository.LikeRepository;
import megane6.weplanet.repository.PostRepository;
import megane6.weplanet.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;

    // 게시판 종류별 목록 조회 - sort 값에 따라 최신순/인기순 선택
    public List<Post> getPostsByBoardType(BoardType boardType, String sort) {
        if ("popular".equals(sort)) {
            return postRepository.findByBoardTypeOrderByLikeCountDescCreatedAtDesc(boardType);
        }
        return postRepository.findByBoardTypeOrderByCreatedAtDesc(boardType);
    }

    // 게시글 작성
    public Post createPost(BoardType boardType, String title, String content, User author) {
        Post post = Post.builder()
                .boardType(boardType)
                .title(title)
                .content(content)
                .author(author)
                .build();

        return postRepository.save(post);
    }

    // 게시글 상세 조회 (없으면 예외)
    public Post getPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다. id=" + postId));
    }

    // 좋아요 토글 (눌려있으면 취소, 안 눌려있으면 추가) - Post.likeCount도 함께 갱신
    public boolean toggleLike(Post post, User user) {
        Optional<Like> existing = likeRepository.findByPostAndUser(post, user);

        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            post.setLikeCount(post.getLikeCount() - 1);
            postRepository.save(post);
            return false; // 취소됨
        } else {
            Like like = Like.builder()
                    .post(post)
                    .user(user)
                    .build();
            likeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);
            return true; // 새로 눌림
        }
    }

    // 게시글 삭제 - 작성자 본인만 삭제 가능
    // 좋아요/댓글 삭제 + 게시글 삭제가 하나의 트랜잭션으로 묶여야 해서 @Transactional 필요
    @Transactional
    public void deletePost(Post post, User requester) {
        if (!post.getAuthor().getId().equals(requester.getId())) {
            throw new IllegalStateException("본인이 작성한 게시글만 삭제할 수 있습니다.");
        }

        // 게시글을 참조하는 좋아요/댓글을 먼저 지운 뒤에 게시글을 삭제 (외래키 제약 위반 방지)
        likeRepository.deleteByPost(post);
        commentRepository.deleteByPost(post);
        reportRepository.deleteByPost(post);
        postRepository.delete(post);
    }

    // 게시글 수정 - 작성자 본인 또는 관리자만 수정 가능
    public void updatePost(Post post, String title, String content, User requester) {
        boolean isAuthor = post.getAuthor().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("본인이 작성한 게시글 또는 관리자만 수정할 수 있습니다.");
        }

        post.setTitle(title);
        post.setContent(content);
        postRepository.save(post);
    }
}

