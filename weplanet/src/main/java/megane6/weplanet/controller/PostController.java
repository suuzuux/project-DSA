package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.ReportService;
import megane6.weplanet.service.SummaryService;
import megane6.weplanet.service.TranslateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
    private final PostListModelHelper postListModelHelper;
    private final PostDetailModelHelper postDetailModelHelper;
    private final AuthenticatedUserResolver userResolver;
    private final UserRepository userRepository;
    private final CommentService commentService;
    private final ReportService reportService;
    private final SummaryService summaryService;
    private final TranslateService translateService;

    private User resolveAuthor(AuthenticatedUser principal, Long testUserId) {
        return userResolver.resolve(principal, testUserId);
    }

    private String renderCommentsResponse(
            Post post,
            Long artistId,
            AuthenticatedUser principal,
            Long testUserId,
            String requestedWith,
            Model model
    ) {
        User currentUser = userResolver.resolve(principal, testUserId);
        postDetailModelHelper.populate(model, post, currentUser);

        if (artistId != null) {
            User artistUser = userRepository.findById(artistId)
                    .filter(user -> user.getRole() == Role.ARTIST)
                    .orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
            model.addAttribute("artist", ArtistCardView.from(artistUser));

            if ("fetch".equals(requestedWith)) {
                return "community/fragments/fanComments :: commentsFragment";
            }
            // 게시글 종류(FAN/ARTIST)에 맞는 상세 페이지로 리다이렉트 (예전엔 무조건 /fan/으로 가는 버그가 있었음)
            String tab = post.getBoardType() == BoardType.FAN ? "fan" : "artist";
            return "redirect:/community/" + artistId + "/" + tab + "/" + post.getId();
        }

        if ("fetch".equals(requestedWith)) {
            return "feed/postDetail :: commentsFragment";
        }
        return "redirect:/posts/detail/" + post.getId();
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

    // 게시판 목록 화면 (FEED-02, FEED-03)
    @GetMapping("/posts/{boardType}")
    public String list(
            @PathVariable String boardType,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            @AuthenticationPrincipal AuthenticatedUser principal,
            Model model
    ) {

        // URL의 소문자 문자열("fan")을 enum(BoardType.FAN)으로 변환
        BoardType type = BoardType.valueOf(boardType.toUpperCase());

        // 36번: 아티스트로 로그인한 사람이 팬 게시판을 볼 땐 "Hide from Artists" 글을 목록에서 뺌
        boolean hideFromArtists = type == BoardType.FAN && userResolver.isArtist(principal);
        postListModelHelper.populate(model, type, sort, null, hideFromArtists);

        log.debug("게시판 조회: {}, 정렬: {}, 게시글 수: {}", type, sort,
                ((List<?>) model.getAttribute("posts")).size());

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
            @RequestParam(defaultValue = "1") Long testUserId,
            @RequestParam(required = false) Long artistId,
            // 36번: 팬 게시판 글쓰기 모달의 🔗 링크 첨부 + "Hide from Artists" 토글 (팬 게시판일 때만 의미 있음)
            @RequestParam(required = false) String linkUrl,
            @RequestParam(defaultValue = "false") boolean hiddenFromArtist,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        BoardType type = BoardType.valueOf(boardType.toUpperCase());

        validateContentLength(content);
        if (files != null && files.size() > 10) {
            throw new IllegalArgumentException("첨부파일은 최대 10개까지 등록할 수 있습니다.");
        }

        // 로그인했으면 로그인한 사람이 작성자, 아니면 "테스트 작성자" 드롭다운으로 고른 사람이 작성자
        User tempAuthor = resolveAuthor(principal, testUserId);

        // FEED-01 권한 구분 실제 적용 - 아티스트 게시판은 아티스트만 작성 가능
        if (type == BoardType.ARTIST && tempAuthor.getRole() != Role.ARTIST) {
            throw new IllegalStateException("아티스트 게시판은 아티스트만 작성할 수 있습니다.");
        }

        User communityArtist = null;
        if (artistId != null) {
            communityArtist = userRepository.findById(artistId)
                    .filter(user -> user.getRole() == Role.ARTIST)
                    .orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));

            // 커뮤니티 게시글은 해당 커뮤니티에만 귀속. ARTIST 보드는 그 커뮤니티 본인만 작성 가능
            if (type == BoardType.ARTIST && !tempAuthor.getId().equals(communityArtist.getId())) {
                throw new IllegalStateException("이 커뮤니티의 아티스트만 글을 작성할 수 있습니다.");
            }
        }

        // linkUrl/hiddenFromArtist는 팬 게시판일 때만 유효하게 처리 (아티스트 게시판 글엔 항상 무시)
        boolean effectiveHidden = type == BoardType.FAN && hiddenFromArtist;
        String effectiveLinkUrl = (type == BoardType.FAN && linkUrl != null && !linkUrl.isBlank()) ? linkUrl.trim() : null;

        Post post = postService.createPost(type, title, content, tempAuthor, communityArtist, effectiveLinkUrl, effectiveHidden);
        postService.saveAttachments(post, files);

        log.debug("게시글 작성 완료 : boardType={}, title={}, author={}, artistId={}",
                type, title, tempAuthor.getUsername(), artistId);

        // 와이어프레임 기준: 글쓰기가 목록 위에 뜨는 모달이라, fetch로 왔으면 페이지 이동 없이
        // 최신 목록(postListFragment)만 다시 그려서 돌려주고, 모달은 자바스크립트가 닫음
        if ("fetch".equals(requestedWith)) {
            if (communityArtist != null) {
                // postList 프래그먼트가 FAN/ARTIST 링크를 만들 때 ${artist.id()}를 참조하므로,
                // artist 모델 속성을 꼭 채워줘야 함 (안 채우면 Thymeleaf에서 500 에러 남)
                model.addAttribute("artist", ArtistCardView.from(communityArtist));
                boolean hideFromArtists = type == BoardType.FAN && userResolver.isArtist(principal);
                postListModelHelper.populate(model, type, "latest", communityArtist, hideFromArtists);
                return "community/fragments/postList :: postListFragment";
            }
            return list(boardType, "latest", "fetch", principal, model);
        }

        if (artistId != null) {
            String tab = type == BoardType.FAN ? "fan" : "artist";
            return "redirect:/community/" + artistId + "/" + tab;
        }

        return "redirect:/posts/" + boardType;
    }

    // 게시글 상세 화면 (댓글 목록, 첨부파일 목록 포함)
    @GetMapping("/posts/detail/{id}")
    public String detail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long testUserId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            Model model
    ) {
        Post post = postService.getPost(id);
        User currentUser = resolveAuthor(principal, testUserId);
        postDetailModelHelper.populate(model, post, currentUser);

        return "feed/postDetail";
    }

    // 댓글 작성 (FEED-04)
    @PostMapping("/posts/detail/{id}/comment")
    public String addComment(
            @PathVariable Long id,
            @RequestParam String content,
            @RequestParam(defaultValue = "1") Long testUserId,
            @RequestParam(required = false) Long artistId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        Post post = postService.getPost(id);
        User author = resolveAuthor(principal, testUserId);

        // 와이어프레임 기준: 댓글은 최대 100자
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }
        if (content.length() > 100) {
            throw new IllegalArgumentException("댓글은 100자를 초과할 수 없습니다.");
        }

        commentService.createComment(post, author, content);

        return renderCommentsResponse(post, artistId, principal, testUserId, requestedWith, model);
    }

    // 댓글 삭제 - 작성자 본인만 삭제 가능
    @PostMapping("/posts/detail/{id}/comment/{commentId}/delete")
    public String deleteComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "1") Long testUserId,
            @RequestParam(required = false) Long artistId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        User requester = resolveAuthor(principal, testUserId);

        commentService.deleteComment(commentId, requester);

        Post post = postService.getPost(id);
        return renderCommentsResponse(post, artistId, principal, testUserId, requestedWith, model);
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
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith
    ) {
        Comment comment = commentService.getComment(commentId);
        User reporter = resolveAuthor(principal, testUserId);

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
            @RequestParam(defaultValue = "1") Long testUserId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User user = resolveAuthor(principal, testUserId);

        boolean liked = postService.toggleLike(post, user);
        log.debug("좋아요 토글: postId={}, userId={}, 결과={}", id, testUserId, liked ? "눌림" : "취소");

        return Map.of(
                "liked", liked,
                "likeCount", post.getLikeCount()
        );
    }

    /**
     * 북마크 토글 - 좋아요 토글과 완전히 같은 방식.
     */
    @PostMapping("/posts/detail/{id}/bookmark")
    @ResponseBody
    public Map<String, Object> bookmark(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long testUserId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User user = resolveAuthor(principal, testUserId);

        boolean bookmarked = postService.toggleBookmark(post, user);
        log.debug("북마크 토글: postId={}, userId={}, 결과={}", id, testUserId, bookmarked ? "눌림" : "취소");

        return Map.of("bookmarked", bookmarked);
    }

    // 게시글 삭제 - 본인 글만 삭제 가능. 삭제 후에는 그 게시글이 있던 게시판 목록으로 돌아감
    @PostMapping("/posts/detail/{id}/delete")
    public String deletePost(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long testUserId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User requester = resolveAuthor(principal, testUserId);

        postService.deletePost(post, requester);

        return "redirect:/posts/" + post.getBoardType().name().toLowerCase();
    }

    // 게시글 신고 - 같은 사람이 같은 글을 중복 신고하면 예외. 댓글 신고와 완전히 같은 구조
    @PostMapping("/posts/detail/{id}/report")
    public Object reportPost(
            @PathVariable Long id,
            @RequestParam ReportReason reason,
            @RequestParam(defaultValue = "1") Long testUserId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith
    ) {
        Post post = postService.getPost(id);
        User reporter = resolveAuthor(principal, testUserId);

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
            @RequestParam(defaultValue = "1") Long testUserId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User requester = resolveAuthor(principal, testUserId);

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

    // 게시글 번역보기 (와이어프레임: 게시글/댓글 본문 밑에 있는 "번역보기" 링크)
    @PostMapping("/posts/detail/{id}/translate")
    @ResponseBody
    public Map<String, Object> translatePost(@PathVariable Long id) {
        Post post = postService.getPost(id);
        String translated = translateService.translate(post.getContent());

        return Map.of("translated", translated);
    }

    // 댓글 번역보기 - 게시글 번역과 완전히 같은 방식
    @PostMapping("/posts/detail/{id}/comment/{commentId}/translate")
    @ResponseBody
    public Map<String, Object> translateComment(@PathVariable Long id, @PathVariable Long commentId) {
        Comment comment = commentService.getComment(commentId);
        String translated = translateService.translate(comment.getContent());

        return Map.of("translated", translated);
    }
}
