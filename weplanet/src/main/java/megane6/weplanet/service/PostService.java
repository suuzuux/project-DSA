package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

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
}
