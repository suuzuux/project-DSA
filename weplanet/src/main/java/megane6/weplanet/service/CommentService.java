package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Like;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.repository.LikeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;

    // 댓글 목록 조회
    public List<Comment> getComments(Post post) {
        return commentRepository.findByPostOrderByCreatedAtAsc(post);
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

    // 좋아요 개수 조회
    public long getLikeCount(Post post) {
        return likeRepository.countByPost(post);
    }

    // 좋아요 토글 (눌려있으면 최소, 안 눌려있으면 추가)
    public boolean toggleLike(Post post, User user) {
        Optional<Like> existing = likeRepository.findByPostAndUser(post, user);

        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            return false; // 취소함
        } else {
            Like like = Like.builder()
                    .post(post)
                    .user(user)
                    .build();
            likeRepository.save(like);
            return true; // 새로 눌림
        }
    }
}
