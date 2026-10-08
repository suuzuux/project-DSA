package megane6.weplanet.controller.fan.community;
import megane6.weplanet.service.fan.ReportService;
import megane6.weplanet.service.comment.CommentService;
import megane6.weplanet.service.fan.PostService;
import megane6.weplanet.service.main.SummaryService;
import megane6.weplanet.service.main.TranslateService;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 게시판 컨트롤러 - X-Requested-With: fetch 요청이면 화면 대신 JSON 이나 프래그먼트만 돌려준다. */
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
    // AI 요약·번역 권한 확인용 (커뮤니티 가입 여부 기준).
    private final CommunityJoinService communityJoinService;
    private final PortalManagementService portalManagementService;
    // 예외는 메시지 키로 던지고(GlobalExceptionHandler 가 번역), 성공 JSON 문구만 여기서 번역한다.
    private final Messages messages;
    private final CommunityArtistResolver communityArtistResolver;

    private String renderCommentsResponse(
            Post post,
            Long artistId,
            AuthenticatedUser principal,
            String requestedWith,
            Model model
    ) {
        User currentUser = userResolver.requireAuthenticated(principal);
        postDetailModelHelper.populate(model, post, currentUser, artistId);

        if (artistId != null) {
            User artistUser = userRepository.findById(artistId)
                    .filter(user -> user.getRole() == Role.ARTIST)
                    .orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
            model.addAttribute("artist", portalManagementService.toArtistCard(artistUser));

            if ("fetch".equals(requestedWith)) {
                return "community/fragments/fanComments :: commentsFragment";
            }
            // 게시글 종류에 맞는 상세 페이지로 리다이렉트한다.
            String tab = post.getBoardType() == BoardType.FAN ? "fan" : "artist";
            return "redirect:/community/" + artistId + "/" + tab + "/" + post.getId();
        }

        if ("fetch".equals(requestedWith)) {
            return "feed/postDetail :: commentsFragment";
        }
        return "redirect:/posts/detail/" + post.getId();
    }

    // 본문은 최대 1,000자, 공백만 있는 내용은 불가 (API 직접 호출 우회 방지).
    private void validateContentLength(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("error.post.contentRequired");
        }
        if (content.length() > 1000) {
            throw new IllegalArgumentException("error.post.contentTooLong");
        }
    }

    // 게시판 목록 화면
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

        // 아티스트가 팬 게시판을 볼 땐 Hide from Artists 글을 뺀다.
        boolean hideFromArtists = type == BoardType.FAN && userResolver.isArtist(principal);
        postListModelHelper.populate(model, type, sort, null, hideFromArtists);

        log.debug("게시판 조회: {}, 정렬: {}, 게시글 수: {}", type, sort,
                ((List<?>) model.getAttribute("posts")).size());

        // 정렬 버튼(fetch)이면 목록 프래그먼트만 돌려준다.
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

    // 글쓰기 저장 (권한 확인, 파일 첨부)
    @PostMapping("/posts/{boardType}/new")
    public String create(
            @PathVariable String boardType,
            @RequestParam(required = false) String title,
            @RequestParam String content,
            // 여러 파일 첨부 (선택하지 않아도 됨)
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) Long artistId,
            // 팬 게시판 링크 첨부와 Hide from Artists 토글
            @RequestParam(required = false) String linkUrl,
            @RequestParam(defaultValue = "false") boolean hiddenFromArtist,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        BoardType type = BoardType.valueOf(boardType.toUpperCase());

        validateContentLength(content);
        if (files != null && files.size() > 10) {
            throw new IllegalArgumentException("error.post.tooManyAttachments");
        }

        User tempAuthor = userResolver.requireAuthenticated(principal);

        // 아티스트 게시판은 해당 커뮤니티 아티스트(솔로·멤버)만 작성한다.
        if (type == BoardType.ARTIST && !tempAuthor.isArtistSide()) {
            throw new IllegalStateException("error.post.artistBoardArtistOnly");
        }
        
        User communityArtist = null;
        if (artistId != null) {
            communityArtist = userRepository.findById(artistId)
                    .filter(user -> user.getRole() == Role.ARTIST)
                    .orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));

            if (type == BoardType.ARTIST && !communityArtistResolver.isArtistOf(tempAuthor, communityArtist.getId())) {
                throw new IllegalStateException("error.post.artistBoardOwnerOnly");
            }
        }
        
        // 팬 게시판: 팬 + (타 커뮤니티를 방문한 아티스트/멤버, 팬과 동일 권한)
        if (type == BoardType.FAN) {
            boolean visitingArtistAsFan = tempAuthor.isArtistSide()
                    && communityArtist != null
                    && !communityArtistResolver.isArtistOf(tempAuthor, communityArtist.getId());
            if (tempAuthor.getRole() != Role.FAN && !visitingArtistAsFan) {
                throw new IllegalStateException("error.post.fanBoardFanOnly");
            }
        }

        // linkUrl·hiddenFromArtist 는 팬 게시판에서만 적용한다.
        boolean effectiveHidden = type == BoardType.FAN && hiddenFromArtist;
        String effectiveLinkUrl = (type == BoardType.FAN && linkUrl != null && !linkUrl.isBlank()) ? linkUrl.trim() : null;

        Post post = postService.createPost(type, title, content, tempAuthor, communityArtist, effectiveLinkUrl, effectiveHidden);
        postService.saveAttachments(post, files);

        log.debug("게시글 작성 완료 : boardType={}, title={}, author={}, artistId={}",
                type, title, tempAuthor.getUsername(), artistId);

        // fetch 요청이면 최신 목록 프래그먼트만 돌려준다 (모달은 JS 가 닫음).
        if ("fetch".equals(requestedWith)) {
            if (communityArtist != null) {
                // postList 프래그먼트가 artist 를 참조하므로 반드시 채운다.
                model.addAttribute("artist", portalManagementService.toArtistCard(communityArtist));
                boolean hideFromArtists = type == BoardType.FAN && userResolver.isArtist(principal);
                postListModelHelper.populateCommunityPage(
                        model, type, "latest", communityArtist, hideFromArtists, tempAuthor, 0);
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
            @AuthenticationPrincipal AuthenticatedUser principal,
            Model model
    ) {
        Post post = postService.getPost(id);

        // 커뮤니티 글이면 접근 제어가 있는 커뮤니티 상세로 보낸다.
        if (post.getArtist() != null) {
            String tab = post.getBoardType() == BoardType.FAN ? "fan" : "artist";
            return "redirect:/community/" + post.getArtist().getId() + "/" + tab + "/" + post.getId();
        }

        User currentUser = userResolver.requireAuthenticated(principal);
        postDetailModelHelper.populate(model, post, currentUser);

        return "feed/postDetail";
    }

    // 댓글 작성
    @PostMapping("/posts/detail/{id}/comment")
    public String addComment(
            @PathVariable Long id,
            @RequestParam String content,
            @RequestParam(required = false) Long artistId,
            // 답글이면 부모 댓글 id (없으면 일반 댓글)
            @RequestParam(required = false) Long parentId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        Post post = postService.getPost(id);
        User author = userResolver.requireAuthenticated(principal);

        // 댓글은 최대 100자
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("error.comment.contentRequired");
        }
        if (content.length() > 100) {
            throw new IllegalArgumentException("error.comment.contentTooLong");
        }

        Comment parent = parentId != null ? commentService.getComment(parentId) : null;
        commentService.createComment(post, author, content, parent);

        return renderCommentsResponse(post, artistId, principal, requestedWith, model);
    }

    // 댓글 삭제 - 작성자 본인만 삭제 가능
    @PostMapping("/posts/detail/{id}/comment/{commentId}/delete")
    public String deleteComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestParam(required = false) Long artistId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        User requester = userResolver.requireAuthenticated(principal);

        commentService.deleteComment(commentId, requester);

        Post post = postService.getPost(id);
        return renderCommentsResponse(post, artistId, principal, requestedWith, model);
    }

    // 댓글 수정 - 작성자 본인만 (댓글 영역을 다시 그려 돌려줌)
    @PostMapping("/posts/detail/{id}/comment/{commentId}/edit")
    public String editComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestParam String content,
            @RequestParam(required = false) Long artistId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        User requester = userResolver.requireAuthenticated(principal);

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("error.comment.contentRequired");
        }
        if (content.length() > 100) {
            throw new IllegalArgumentException("error.comment.contentTooLong");
        }

        commentService.updateComment(commentId, requester, content);

        Post post = postService.getPost(id);
        return renderCommentsResponse(post, artistId, principal, requestedWith, model);
    }

    /** 댓글 신고 - fetch 면 JSON, 일반 요청이면 리다이렉트를 돌려준다. */
    @PostMapping("/posts/detail/{id}/comment/{commentId}/report")
    public Object reportComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestParam ReportReason reason,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith
    ) {
        Comment comment = commentService.getComment(commentId);
        User reporter = userResolver.requireAuthenticated(principal);

        reportService.reportComment(comment, reporter, reason);

        if ("fetch".equals(requestedWith)) {
            return ResponseEntity.ok(Map.of("success", true, "message", messages.get("community.report.commentReported")));
        }

        return "redirect:" + communityDetailPath(postService.getPost(id));
    }

    /** 좋아요 토글 (항상 JSON 응답). */
    @PostMapping("/posts/detail/{id}/like")
    @ResponseBody
    public Map<String, Object> like(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User user = userResolver.requireAuthenticated(principal);

        boolean liked = postService.toggleLike(post, user);
        log.debug("좋아요 토글: postId={}, userId={}, 결과={}", id, user.getId(), liked ? "눌림" : "취소");

        return Map.of(
                "liked", liked,
                "likeCount", post.getLikeCount()
        );
    }

    /** 북마크 토글 (좋아요와 같은 방식). */
    @PostMapping("/posts/detail/{id}/bookmark")
    @ResponseBody
    public Map<String, Object> bookmark(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User user = userResolver.requireAuthenticated(principal);

        boolean bookmarked = postService.toggleBookmark(post, user);
        log.debug("북마크 토글: postId={}, userId={}, 결과={}", id, user.getId(), bookmarked ? "눌림" : "취소");

        return Map.of("bookmarked", bookmarked);
    }

    // 게시글 삭제 - 본인 글만, 삭제 후 커뮤니티 게시판 목록으로 이동한다.
    @PostMapping("/posts/detail/{id}/delete")
    public String deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User requester = userResolver.requireAuthenticated(principal);
        BoardType boardType = post.getBoardType();
        User artist = post.getArtist();

        postService.deletePost(post, requester);

        // 커뮤니티 게시판 목록으로 돌아간다.
        if (artist != null) {
            String tab = boardType == BoardType.ARTIST ? "artist" : "fan";
            return "redirect:/community/" + artist.getId() + "/" + tab;
        }
        return "redirect:/posts/" + boardType.name().toLowerCase();
    }

    // 게시글 신고 - 같은 글 중복 신고는 예외.
    @PostMapping("/posts/detail/{id}/report")
    public Object reportPost(
            @PathVariable Long id,
            @RequestParam ReportReason reason,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith
    ) {
        Post post = postService.getPost(id);
        User reporter = userResolver.requireAuthenticated(principal);

        reportService.reportPost(post, reporter, reason);

        if ("fetch".equals(requestedWith)) {
            return ResponseEntity.ok(Map.of("success", true, "message", messages.get("community.report.postReported")));
        }

        return "redirect:" + communityDetailPath(post);
    }

    // 수정 폼 (레거시 feed 페이지용).
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
            @RequestParam(required = false) String title,
            @RequestParam String content,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User requester = userResolver.requireAuthenticated(principal);

        validateContentLength(content);
        postService.updatePost(post, title, content, requester);

        return "redirect:" + communityDetailPath(post);
    }

    // 삭제·신고·수정 후 돌아갈 커뮤니티 상세 경로.
    private String communityDetailPath(Post post) {
        User artist = post.getArtist();
        if (artist == null) {
            return "/posts/detail/" + post.getId();
        }
        String tab = post.getBoardType() == BoardType.ARTIST ? "artist" : "fan";
        return "/community/" + artist.getId() + "/" + tab + "/" + post.getId();
    }

    /** AI 요약·번역 권한 확인 - 로그인과 커뮤니티 가입 여부 (Gemini 호출 남용·전용 글 유출 방지). */
    private User requireAiAccess(Post post, AuthenticatedUser principal) {
        User user = userResolver.requireAuthenticated(principal);

        User artist = post.getArtist();
        if (artist == null) {
            return user; // 커뮤니티에 속하지 않은 레거시 게시글은 로그인만 확인
        }
        // 그 커뮤니티 아티스트 본인은 당연히 열람 가능
        if (artist.getId().equals(user.getId())) {
            return user;
        }
        if (!communityJoinService.isJoined(user, artist.getId())) {
            throw new IllegalStateException("error.community.joinRequired");
        }
        return user;
    }

    /** AI 요약 - 저장하지 않고 요청마다 Gemini 로 생성한다 (fetch 전용). */
    @PostMapping("/posts/detail/{id}/summarize")
    @ResponseBody
    public Map<String, Object> summarizePost(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User user = requireAiAccess(post, principal);
        // 요약도 사용자의 기본 서비스 언어로 받는다
        String summary = summaryService.summarize(post.getContent(), user.getPreferredLanguage());

        return Map.of("summary", summary);
    }

    // 게시글 번역보기
    @PostMapping("/posts/detail/{id}/translate")
    @ResponseBody
    public Map<String, Object> translatePost(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User user = requireAiAccess(post, principal);
        // 로그인 사용자의 "기본 서비스 언어"로 번역
        String translated = translateService.translate(post.getContent(), user.getPreferredLanguage());

        return Map.of("translated", translated);
    }

    // 댓글 번역보기
    @PostMapping("/posts/detail/{id}/comment/{commentId}/translate")
    @ResponseBody
    public Map<String, Object> translateComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        Post post = postService.getPost(id);
        User user = requireAiAccess(post, principal);
        Comment comment = commentService.getComment(commentId);
        String translated = translateService.translate(comment.getContent(), user.getPreferredLanguage());

        return Map.of("translated", translated);
    }
}
