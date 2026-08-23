package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.ReportService;
import megane6.weplanet.service.SummaryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final UserRepository userRepository;
    private final CommentService commentService;
    private final ReportService reportService;
    private final SummaryService summaryService;

    // 테스트용 유저 조회 공통 헬퍼 - 없으면 예외 (로그인 완성되면 이 메서드 자체가 통째로 없어질 예정)
    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + userId + ")가 없습니다."));
    }

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
        User tempAuthor = getUserOrThrow(testUserId);

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
        User author = getUserOrThrow(testUserId);

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
        User requester = getUserOrThrow(testUserId);

        commentService.deleteComment(commentId, requester);

        return "redirect:/posts/detail/" + id;
    }

    // 좋아요 토글 (비동기) - 페이지 새로고침 없이 결과(JSON)만 반환
    @PostMapping("/posts/detail/{id}/like")
    @ResponseBody
    public Map<String, Object> like(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        Post post = postService.getPost(id);
        User user = getUserOrThrow(testUserId);

        boolean liked = postService.toggleLike(post, user);
        log.debug("좋아요 토글: postId={}, userId={}, 결과={}", id, testUserId, liked ? "눌림" : "취소");

        return Map.of(
                "liked", liked,
                "likeCount", post.getLikeCount()
        );
    }

    // 게시글 삭제 - 본인 글만 삭제 가능
    @PostMapping("/posts/detail/{id}/delete")
    public String deletePost(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        Post post = postService.getPost(id);
        User requester = getUserOrThrow(testUserId);

        postService.deletePost(post, requester);

        return "redirect:/posts/" + post.getBoardType().name().toLowerCase();
    }

    // 게시글 신고 - 같은 사람이 같은 글 중복 신고 시 예외
    @PostMapping("/posts/detail/{id}/report")
    public String reportPost(
            @PathVariable Long id,
            @RequestParam ReportReason reason,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        Post post = postService.getPost(id);
        User reporter = getUserOrThrow(testUserId);

        reportService.reportPost(post, reporter, reason);

        return "redirect:/posts/detail/" + id;
    }

    // 수정 폼 화면 이동 - 기존 제목/내용을 미리 채워서 보여줌
    @GetMapping("/posts/detail/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            Model model
    ) {
        Post post = postService.getPost(id);
        model.addAttribute("post", post);
        model.addAttribute("boardType", post.getBoardType());

        return "postForm";
    }

    // 수정 저장 처리 - 작성자 본인 또는 관리자만 가능
    @PostMapping("/posts/detail/{id}/edit")
    public String updatePost(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        Post post = postService.getPost(id);
        User requester = getUserOrThrow(testUserId);

        postService.updatePost(post, title, content, requester);

        return "redirect:/posts/detail/" + id;
    }

    // AI 자동 요약 - 게시글 내용을 요약해서 보여줌 (저장하지 않고 요청할 때마다 새로 생성)
    @PostMapping("/posts/detail/{id}/summarize")
    public String summarizePost(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        Post post = postService.getPost(id);
        String summary = summaryService.summarize(post.getContent());

        redirectAttributes.addFlashAttribute("summary", summary);

        return "redirect:/posts/detail/" + id;
    }
}
