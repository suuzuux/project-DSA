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

    // 게시판 종류별 목록 조회
    public List<Post> getPostsByBoardType(BoardType boardType) {
        return postRepository.findByBoardTypeOrderByCreatedAtDesc(boardType);
    }

    // 게시글 작성 (임시로 작성자 이름만 문자열로 받음 - User 완성되면 파라미터 교체 예정)
    public Post createPost(BoardType boardType, String title, String content, User author) {
        Post post = Post.builder()
                .boardType(boardType)
                .title(title)
                .content(content)
                .author(author)
                .build();

        return postRepository.save(post);
    }
}
