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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 게시판(FEED) 관련 화면과 요청을 처리하는 컨트롤러.
 * <p>
 * 이 컨트롤러의 메서드들은 크게 두 가지 방식으로 응답함.
 * <p>
 * ① 예전 방식(web1~web5에서 쓰던 방식) : return "화면이름"; 또는 return "redirect:/주소";
 * → 브라우저가 페이지 전체를 다시 불러옴 (화면이 한 번 깜빡이고 새로고침됨)
 * <p>
 * ② 비동기(fetch) 방식 : 자바스크립트가 fetch()로 요청을 보내고, 응답을 JSON이나
 * 화면 조각(fragment)만 받아서 페이지의 일부만 바꿔치기함 (전체 새로고침 없음).
 * 이 컨트롤러에서는 요청 헤더에 X-Requested-With: fetch 가 붙어있는지로
 * "이 요청이 자바스크립트가 몰래 보낸 fetch 요청인지, 사람이 폼(form)을 눌러서 보낸 일반 요청인지"를 구분함.
 * fetch로 온 요청이면 화면 전체 대신 결과(JSON)나 화면의 일부(fragment)만 돌려줌.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final UserRepository userRepository;
    private final CommentService commentService;
    private final ReportService reportService;
    private final SummaryService summaryService;

    // 테스트용 유저 조회 공통 헬퍼 - 없으면 예외.
    // (로그인 기능이 아직 없어서, 화면에서 "테스트 작성자" 드롭다운으로 누구인 척 할지 골라서 testUserId로 넘겨받음.
    //  형준님이 로그인 기능을 완성하면, 이 메서드와 testUserId 파라미터들은 전부
    //  "로그인된 사용자 정보를 세션에서 꺼내오는 방식"으로 교체될 예정)
    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + userId + ")가 없습니다."));
    }

    // 와이어프레임 기준: 본문은 공백/줄바꿈 포함 최대 1,000자, 공백만 있는 내용은 등록 불가.
    // 프론트(JS)에서도 막지만, 서버에서도 한 번 더 검증해서 API를 직접 호출하는 우회를 막음
    private void validateContentLength(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
        if (content.length() > 1000) {
            throw new IllegalArgumentException("내용은 1,000자를 초과할 수 없습니다.");
        }
    }

    // 댓글 목록을 "아티스트가 쓴 댓글"과 "그 외(팬 등) 댓글"로 나눠서 모델에 담는 공통 헬퍼.
    // 와이어프레임 기준: 아티스트 댓글은 항상 화면 맨 위 별도 박스에 고정 표시, 나머지는 그 아래 "전체 댓글"에 표시
    private void addCommentAttributes(Model model, Post post) {
        List<Comment> comments = commentService.getComments(post);
        List<Comment> artistComments = comments.stream()
                .filter(c -> c.getAuthor().getRole() == Role.ARTIST)
                .toList();
        List<Comment> otherComments = comments.stream()
                .filter(c -> c.getAuthor().getRole() != Role.ARTIST)
                .toList();

        model.addAttribute("artistComments", artistComments);
        model.addAttribute("otherComments", otherComments);
    }

    // 게시판 목록 화면 (FEED-02, FEED-03)
    @GetMapping("/posts/{boardType}")
    public String list(
            @PathVariable String boardType,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {

        // URL의 소문자 문자열("fan")을 enum(BoardType.FAN)으로 변환
        BoardType type = BoardType.valueOf(boardType.toUpperCase());

        List<Post> posts = postService.getPostsByBoardType(type, sort);

        log.debug("게시판 조회: {}, 정렬: {}, 게시글 수: {}", type, sort, posts.size());

        // 게시글 목록 카드에 댓글 개수 + 대표 이미지(첫 번째 이미지 첨부파일)도 같이 보여주기 위해 미리 계산해둠
        // (와이어프레임: 좋아요/댓글 숫자가 0이면 숫자 자체를 표시하지 않음, 카드 안에 사진 영역 표시)
        Map<Long, Long> commentCounts = new HashMap<>();
        Map<Long, String> thumbnailUrls = new HashMap<>();
        for (Post post : posts) {
            commentCounts.put(post.getId(), commentService.getCommentCount(post));

            postService.getAttachments(post).stream()
                    .filter(a -> a.isImage())
                    .findFirst()
                    .ifPresent(a -> thumbnailUrls.put(post.getId(), a.getStoredName()));
        }

        model.addAttribute("posts", posts);
        model.addAttribute("boardType", type);
        model.addAttribute("sort", sort);
        model.addAttribute("commentCounts", commentCounts);
        model.addAttribute("thumbnailUrls", thumbnailUrls);

        // 정렬 버튼을 클릭했을 때(자바스크립트 fetch로 온 요청)는,
        // "postList.html 안에서 postListFragment 라는 이름표가 붙은 부분만" 잘라서 돌려줌
        // → 페이지 전체가 아니라 게시글 목록 표(<div id="postListArea">)만 바뀌므로 새로고침 없이 정렬이 바뀜
        if ("fetch".equals(requestedWith)) {
            return "feed/postList :: postListFragment";
        }

        return "feed/postList";
    }

    // 글쓰기 폼 화면 이동
    @GetMapping("/posts/{boardType}/new")
    public String newForm(
            @PathVariable String boardType,
            Model model
    ) {
        BoardType type = BoardType.valueOf(boardType.toUpperCase());
        model.addAttribute("boardType", type);

        return "feed/postForm";
    }

    // 글쓰기 저장 처리 (FEED-01 권한 구분, FEED-02 작성, FEED-10 파일 첨부)
    @PostMapping("/posts/{boardType}/new")
    public String create(
            @PathVariable String boardType,
            @RequestParam String title,
            @RequestParam String content,
            // List<MultipartFile> : 폼에서 <input type="file" multiple>로 여러 개 고른 파일들이
            // 하나의 리스트로 담겨서 들어옴. required=false라서 파일을 하나도 안 골라도 에러 안 남
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(defaultValue = "1") Long testUserId
    ) {
        BoardType type = BoardType.valueOf(boardType.toUpperCase());

        validateContentLength(content);
        if (files != null && files.size() > 10) {
            throw new IllegalArgumentException("첨부파일은 최대 10개까지 등록할 수 있습니다.");
        }

        // 임시 - 로그인 기능 완성 전까지는 폼에서 넘어온 testUserId로 작성자를 선택
        User tempAuthor = getUserOrThrow(testUserId);

        // FEED-01 권한 구분 실제 적용 - 아티스트 게시판은 아티스트만 작성 가능
        if (type == BoardType.ARTIST && tempAuthor.getRole() != Role.ARTIST) {
            throw new IllegalStateException("아티스트 게시판은 아티스트만 작성할 수 있습니다.");
        }

        Post post = postService.createPost(type, title, content, tempAuthor);
        postService.saveAttachments(post, files);

        log.debug("게시글 작성 완료 : boardType={}, title={}, author={}", type, title, tempAuthor.getUsername());

        return "redirect:/posts/" + boardType;
    }

    // 게시글 상세 화면 (댓글 목록, 첨부파일 목록 포함)
    @GetMapping("/posts/detail/{id}")
    public String detail(
            @PathVariable Long id,
            Model model
    ) {
        Post post = postService.getPost(id);
        List<Comment> comments = commentService.getComments(post);

        model.addAttribute("post", post);
        model.addAttribute("comments", comments);
        model.addAttribute("attachments", postService.getAttachments(post));
        addCommentAttributes(model, post);

        return "feed/postDetail";
    }

    // 댓글 작성 (FEED-04)
    @PostMapping("/posts/detail/{id}/comment")
    public String addComment(
            @PathVariable Long id,
            @RequestParam String content,
            @RequestParam(defaultValue = "1") Long testUserId,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        Post post = postService.getPost(id);
        User author = getUserOrThrow(testUserId);

        // 와이어프레임 기준: 댓글은 최대 100자
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }
        if (content.length() > 100) {
            throw new IllegalArgumentException("댓글은 100자를 초과할 수 없습니다.");
        }

        commentService.createComment(post, author, content);

        // fetch로 온 요청(비동기 댓글 작성)이면, 댓글 목록만 다시 조회해서
        // postDetail.html 안의 "commentsFragment" 부분만 새로 그려서 돌려줌
        if ("fetch".equals(requestedWith)) {
            model.addAttribute("post", post);
            model.addAttribute("comments", commentService.getComments(post));
            addCommentAttributes(model, post);
            return "feed/postDetail :: commentsFragment";
        }

        return "redirect:/posts/detail/" + id;
    }

    // 댓글 삭제 - 작성자 본인만 삭제 가능
    @PostMapping("/posts/detail/{id}/comment/{commentId}/delete")
    public String deleteComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "1") Long testUserId,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        User requester = getUserOrThrow(testUserId);

        commentService.deleteComment(commentId, requester);

        if ("fetch".equals(requestedWith)) {
            Post post = postService.getPost(id);
            model.addAttribute("post", post);
            model.addAttribute("comments", commentService.getComments(post));
            addCommentAttributes(model, post);
            return "feed/postDetail :: commentsFragment";
        }

        return "redirect:/posts/detail/" + id;
    }

    /**
     * 댓글 신고 - 게시글 신고와 완전히 같은 방식 (같은 사람이 같은 댓글을 두 번 신고하면 예외).
     * <p>
     * 반환 타입이 Object인 이유 : 상황에 따라 서로 다른 두 가지 타입을 돌려줘야 하기 때문.
     * - fetch로 온 경우 → ResponseEntity(JSON 형태의 응답)
     * - 일반 폼 제출인 경우 → String("redirect:...", 화면 이동 경로)
     * 자바 메서드는 하나의 반환 타입만 가질 수 있어서, 이 둘의 공통 부모 타입인 Object로 선언함.
     */
    @PostMapping("/posts/detail/{id}/comment/{commentId}/report")
    public Object reportComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestParam ReportReason reason,
            @RequestParam(defaultValue = "1") Long testUserId,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith
    ) {
        Comment comment = commentService.getComment(commentId);
        User reporter = getUserOrThrow(testUserId);

        reportService.reportComment(comment, reporter, reason);

        if ("fetch".equals(requestedWith)) {
            // ResponseEntity.ok(...) : "성공(HTTP 200)" 상태와 함께 괄호 안의 내용을 JSON으로 돌려줌
            return ResponseEntity.ok(Map.of("success", true, "message", "댓글 신고가 접수되었습니다."));
        }

        return "redirect:/posts/detail/" + id;
    }

    /**
     * 좋아요 토글.
     * <p>
     *
     * @ResponseBody : 이 메서드가 돌려주는 값(Map)을 "화면 이름"이 아니라
     * "JSON 데이터 그 자체"로 브라우저에 돌려주라는 표시. 좋아요 버튼은 항상 비동기로만 동작하므로
     * (fetch로 안 왔는지 구분할 필요 없이) 무조건 JSON을 돌려주면 됨.
     */
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

    // 게시글 삭제 - 본인 글만 삭제 가능. 삭제 후에는 그 게시글이 있던 게시판 목록으로 돌아감
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

    // 게시글 신고 - 같은 사람이 같은 글을 중복 신고하면 예외. 댓글 신고와 완전히 같은 구조
    @PostMapping("/posts/detail/{id}/report")
    public Object reportPost(
            @PathVariable Long id,
            @RequestParam ReportReason reason,
            @RequestParam(defaultValue = "1") Long testUserId,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith
    ) {
        Post post = postService.getPost(id);
        User reporter = getUserOrThrow(testUserId);

        reportService.reportPost(post, reporter, reason);

        if ("fetch".equals(requestedWith)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "게시글 신고가 접수되었습니다."));
        }

        return "redirect:/posts/detail/" + id;
    }

    // 수정 폼 화면 이동 - 기존 제목/내용을 미리 채워서 보여줌 (postForm.html을 글쓰기 화면과 공유해서 씀)
    @GetMapping("/posts/detail/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            Model model
    ) {
        Post post = postService.getPost(id);
        model.addAttribute("post", post);
        model.addAttribute("boardType", post.getBoardType());

        return "feed/postForm";
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

        validateContentLength(content);
        postService.updatePost(post, title, content, requester);

        return "redirect:/posts/detail/" + id;
    }

    /**
     * AI 자동 요약 - 게시글 내용을 Gemini에게 보내 요약을 받아옴 (저장하지 않고, 버튼 누를 때마다 새로 생성).
     * <p>
     * 이 버튼은 항상 비동기(fetch)로만 동작함. AI가 답을 만드는 데 몇 초 걸릴 수 있는데,
     * 그동안 화면이 멈추지 않고 "AI가 요약을 만들고 있어요..." 같은 로딩 문구를 보여줄 수 있는 이유가
     * 바로 fetch 방식이기 때문임 (페이지 전체를 새로고침하며 기다리는 게 아니라,
     * 자바스크립트가 백그라운드에서 응답을 기다리는 동안에도 화면 조작이 가능함).
     */
    @PostMapping("/posts/detail/{id}/summarize")
    @ResponseBody
    public Map<String, Object> summarizePost(@PathVariable Long id) {
        Post post = postService.getPost(id);
        String summary = summaryService.summarize(post.getContent());

        return Map.of("summary", summary);
    }
}
