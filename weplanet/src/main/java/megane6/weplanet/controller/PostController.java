package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final UserRepository userRepository;
    private final CommentService commentService;

    @GetMapping("/posts/{boardType}")
    public String list(
            @PathVariable String boardType,
            @RequestParam(defaultValue = "latest") String sort,
            Model model
    ) {

        // URL의 소문자 문자열 ("fan")을 enum(FAN)으로 변환
        BoardType type = BoardType.valueOf(boardType.toUpperCase());

        List<Post> posts = postService.getPostsByBoardType(type, sort);

        log.debug("게시판 조회: {}, 정렬: {}, 게시글 수: {}", type, sort, posts.size());

        model.addAttribute("posts", posts);
        model.addAttribute("boardType", type);
        model.addAttribute("sort", sort);

        return "postList";
    }

    // 글쓰기 폼 화면 이동
    @GetMapping("/posts/{boardType}/new")
    public String newForm(
            @PathVariable String boardType,
            Model model
    ) {
        BoardType type = BoardType.valueOf(boardType.toUpperCase());
        model.addAttribute("boardType", type);

        return "postForm";
    }

    // 글쓰기 저장 처리
    @PostMapping("/posts/{boardType}/new")
    public String create(
            @PathVariable String boardType,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        BoardType type = BoardType.valueOf(boardType.toUpperCase());

        // 임시 - 로그인 기능 완성 전까지는 폼에서 넘어온 testUserId로 작성자를 선택
        // 형준님 로그인 완성되면 이 블록 전체를 세션에서 꺼낸 실제 User로 교체 예정
        User tempAuthor = userRepository.findById(testUserId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + testUserId + ")가 없습니다."));

        // FEED-01 권한 구분 실제 적용 - 아티스트 게시판은 아티스트만 작성 가능
        if (type == BoardType.ARTIST && tempAuthor.getRole() != Role.ARTIST) {
            throw new IllegalStateException("아티스트 게시판은 아티스트만 작성할 수 있습니다.");
        }

        postService.createPost(type, title, content, tempAuthor);

        log.debug("게시글 작성 완료 : boardType={}, title={}, author={}", type, title, tempAuthor.getUsername());

        return "redirect:/posts/" + boardType;
    }

    // 게시글 상세 화면 (댓글 목록 포함)
    @GetMapping("/posts/detail/{id}")
    public String detail(
            @PathVariable Long id,
            Model model
    ) {
        Post post = postService.getPost(id);
        List<Comment> comments = commentService.getComments(post);

        model.addAttribute("post", post);
        model.addAttribute("comments", comments);

        return "postDetail";
    }

    // 댓글 작성
    @PostMapping("/posts/detail/{id}/comment")
    public String addComment(
            @PathVariable Long id,
            @RequestParam String content,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        Post post = postService.getPost(id);
        User author = userRepository.findById(testUserId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + testUserId + ")가 없습니다."));

        commentService.createComment(post, author, content);

        return "redirect:/posts/detail/" + id;
    }

    // 댓글 삭제 - 본인 댓글만 삭제 가능
    @PostMapping("/posts/detail/{id}/comment/{commentId}/delete")
    public String deleteComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        User requester = userRepository.findById(testUserId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + testUserId + ")가 없습니다."));

        commentService.deleteComment(commentId, requester);

        return "redirect:/posts/detail/" + id;
    }

    // 좋아요 토글
    @PostMapping("/posts/detail/{id}/like")
    public String like(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        Post post = postService.getPost(id);
        User user = userRepository.findById(testUserId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + testUserId + ")가 없습니다."));

        boolean liked = postService.toggleLike(post, user);
        log.debug("좋아요 토글: postId={}, userId={}, 결과={}", id, testUserId, liked ? "눌림" : "취소");

        return "redirect:/posts/detail/" + id;
    }

    // 게시글 삭제 - 본인 글만 삭제 가능
    @PostMapping("/posts/detail/{id}/delete")
    public String deletePost(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        Post post = postService.getPost(id);
        User requester = userRepository.findById(testUserId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + testUserId + ")가 없습니다."));

        postService.deletePost(post, requester);

        return "redirect:/posts/" + post.getBoardType().name().toLowerCase();
    }
}
