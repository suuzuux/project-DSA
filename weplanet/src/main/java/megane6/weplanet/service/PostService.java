package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.*;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.domain.event.FanPostCreatedEvent;
import megane6.weplanet.repository.*;
import megane6.weplanet.service.email.CommunityActivityNotifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/** 게시글 처리 로직 서비스 (컨트롤러는 요청 분배만 담당). */
@Service
@RequiredArgsConstructor // final 필드 생성자 주입 (롬복)
public class PostService {

    public static final int COMMUNITY_PAGE_SIZE = 10;

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final CommentRepository commentRepository;
    private final CommunityActivityNotifier communityActivityNotifier; // 아티스트 새 글 → 팔로워 이메일
    private final ReportRepository reportRepository;
    private final CommentReportRepository commentReportRepository;
    private final PostAttachmentRepository postAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher; // [배지] 활동 이벤트 발행
    private final ChatFilterService chatFilterService; // 금칙어 검사

    // 게시판 종류별 목록 (최신순·인기순)
    public List<Post> getPostsByBoardType(BoardType boardType, String sort) {
        if ("popular".equals(sort)) {
            return postRepository.findByBoardTypeOrderByLikeCountDescCreatedAtDesc(boardType);
        }
        return postRepository.findByBoardTypeOrderByCreatedAtDesc(boardType);
    }

    // 특정 아티스트 커뮤니티의 게시판 목록
    public List<Post> getPostsByBoardTypeAndArtist(BoardType boardType, User artist, String sort) {
        if ("popular".equals(sort)) {
            return postRepository.findByBoardTypeAndArtistOrderByLikeCountDescCreatedAtDesc(boardType, artist);
        }
        return postRepository.findByBoardTypeAndArtistOrderByCreatedAtDesc(boardType, artist);
    }

    // 커뮤니티 게시판 10개 단위 조회 (Slice 는 COUNT 없이 다음 묶음 여부만 확인).
    public Slice<Post> getCommunityPostSlice(
            BoardType boardType,
            User artist,
            String sort,
            int page,
            boolean hideFromArtists
    ) {
        return getCommunityPostSlice(boardType, artist, sort, page, hideFromArtists, false);
    }

    // mediaOnly: 사진·영상 첨부가 있는 글만
    public Slice<Post> getCommunityPostSlice(
            BoardType boardType,
            User artist,
            String sort,
            int page,
            boolean hideFromArtists,
            boolean mediaOnly
    ) {
        Sort postSort = "popular".equals(sort)
                ? Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), COMMUNITY_PAGE_SIZE, postSort);

        if (mediaOnly) {
            return postRepository.findMediaPostsByBoardTypeAndArtist(boardType, artist, pageable);
        }
        if (hideFromArtists) {
            return postRepository.findByBoardTypeAndArtistAndHiddenFromArtistFalse(
                    boardType, artist, pageable);
        }
        return postRepository.findByBoardTypeAndArtist(boardType, artist, pageable);
    }

    // 내 프로필 포스트 이력
    public List<Post> getPostsByAuthor(User author, boolean oldest) {
        return oldest
                ? postRepository.findByAuthorOrderByCreatedAtAsc(author)
                : postRepository.findByAuthorOrderByCreatedAtDesc(author);
    }

    // 최신 인기 포스트 상위 4개
    public List<Post> getPopularPosts() {
        // 아티스트 게시판 글만 (숨김 글 제외)
        return postRepository.findTop4ByBoardTypeAndHiddenFromArtistFalseAndArtistIsNotNullOrderByLikeCountDescCreatedAtDesc(BoardType.ARTIST);
    }

    // 하이라이트 위젯용 최신 글 6개
    public List<Post> getRecentPosts(BoardType boardType, User artist) {
        return postRepository.findTop6ByBoardTypeAndArtistOrderByCreatedAtDesc(boardType, artist);
    }

    // 게시글 작성
    public Post createPost(BoardType boardType, String title, String content, User author) {
        return createPost(boardType, title, content, author, null, null, false);
    }

    // 커뮤니티 게시판 글쓰기
    public Post createPost(BoardType boardType, String title, String content, User author, User artist) {
        return createPost(boardType, title, content, author, artist, null, false);
    }

    // 링크 첨부와 Hide from Artists 까지 받는 글쓰기
    public Post createPost(BoardType boardType, String title, String content, User author, User artist,
                            String linkUrl, boolean hiddenFromArtist) {
        chatFilterService.rejectIfContainsBannedWord(title, content);

        Post post = Post.builder()
                .boardType(boardType)
                .title(deriveTitle(title, content))
                .content(content)
                .author(author)
                .artist(artist)
                .linkUrl(linkUrl)
                .hiddenFromArtist(hiddenFromArtist)
                .build();

        Post saved = postRepository.save(post);
        // 아티스트 게시판 새 글만 팔로워에게 알린다.
        if (boardType == BoardType.ARTIST && artist != null) {
            communityActivityNotifier.notifyNewPost(artist, saved);
        }
        
        // [배지] 팬 게시판 글만 첫 게시글 배지 대상
        if (boardType == BoardType.FAN && artist != null) {
            eventPublisher.publishEvent(new BadgeActivityEvent(
                    author.getId(), artist.getId(),
                    BadgeActivityEvent.Activity.POST_CREATED
            ));
            // [해시태그 총공] 집계 여부는 HashtagEventPostListener 가 판단
            eventPublisher.publishEvent(new FanPostCreatedEvent(saved.getId()));
        }
        
        return saved;
    }

    /** 첨부파일 저장 - 파일은 디스크에, 파일 정보는 post_attachment 에 기록한다. */
    public void saveAttachments(Post post, List<MultipartFile> files) {
        if (files == null) {
            return;
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue; // 빈 input 은 건너뛴다.
            }

            String storedName = fileStorageService.store(file);

            PostAttachment attachment = PostAttachment.builder()
                    .post(post)
                    .originalName(file.getOriginalFilename())
                    .storedName(storedName)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build();

            postAttachmentRepository.save(attachment);
        }
    }

    // 첨부파일 목록
    public List<PostAttachment> getAttachments(Post post) {
        return postAttachmentRepository.findByPostOrderByIdAsc(post);
    }

    // 게시글 조회 (없으면 예외)
    public Post getPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("error.post.notFound"));
    }

    // 좋아요 토글 (likeCount 도 함께 증감).
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
            // [배지] 커뮤니티 글이면 누른 사람과 글쓴이 모두 확인
            if (post.getArtist() != null) {
                Long artistId = post.getArtist().getId();
                eventPublisher.publishEvent(new BadgeActivityEvent(
                        user.getId(), artistId, BadgeActivityEvent.Activity.LIKE_GIVEN
                ));
                eventPublisher.publishEvent(new BadgeActivityEvent(
                        post.getAuthor().getId(), artistId, BadgeActivityEvent.Activity.LIKE_RECEIVED
                ));
            }
            return true; // 새로 누름
        }
    }

    // 북마크 토글 (카운트 컬럼 없이 개수를 센다).
    public boolean toggleBookmark(Post post, User user) {
        Optional<Bookmark> existing = bookmarkRepository.findByPostAndUser(post, user);

        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            return false; // 취소됨
        } else {
            Bookmark bookmark = Bookmark.builder()
                    .post(post)
                    .user(user)
                    .build();
            bookmarkRepository.save(bookmark);
            return true; // 새로 누름
        }
    }

    // 북마크 여부
    public boolean isBookmarked(Post post, User user) {
        return bookmarkRepository.findByPostAndUser(post, user).isPresent();
    }

    /** 게시글 삭제 - 작성자 또는 관리자 (관련 데이터와 한 트랜잭션으로 삭제). */
    @Transactional
    public void deletePost(Post post, User requester) {
        boolean isAuthor = post.getAuthor().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("error.post.deleteNoPermission");
        }
        deletePostCascade(post);
    }

    /** 아티스트 커뮤니티 글을 에이전시·신고함에서 삭제할 때 사용 */
    @Transactional
    public void deletePostForArtistCommunity(Post post, User artist) {
        if (artist == null || post.getArtist() == null || !post.getArtist().getId().equals(artist.getId())) {
            throw new IllegalStateException("error.post.deleteOnlyThisCommunity");
        }
        deletePostCascade(post);
    }

    private void deletePostCascade(Post post) {
        // 첨부파일은 디스크 파일부터 지운다.
        List<PostAttachment> attachments = postAttachmentRepository.findByPostOrderByIdAsc(post);
        for (PostAttachment attachment : attachments) {
            fileStorageService.delete(attachment.getStoredName());
        }

        // FK 순서대로 자식 데이터를 먼저 지운다 (댓글 신고 → 댓글).
        commentReportRepository.deleteByComment_Post(post);
        likeRepository.deleteByPost(post);
        bookmarkRepository.deleteByPost(post);
        // 답글이 부모 댓글을 참조하므로 답글부터 지운다.
        commentRepository.deleteByPostAndParentIsNotNull(post);
        commentRepository.deleteByPost(post);
        reportRepository.deleteByPost(post);
        postAttachmentRepository.deleteByPost(post);
        postRepository.delete(post);
    }

    // 게시글 수정 - 작성자 또는 관리자
    public void updatePost(Post post, String title, String content, User requester) {
        boolean isAuthor = post.getAuthor().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("error.post.editNoPermission");
        }

        chatFilterService.rejectIfContainsBannedWord(title, content);

        // 변경 감지로도 반영되지만 명시적으로 저장한다.
        post.setTitle(deriveTitle(title, content));
        post.setContent(content);
        postRepository.save(post);
    }

    /** 제목 입력칸이 없어 본문 첫 줄로 제목을 만든다 (목록·관리자 화면 미리보기용). */
    private String deriveTitle(String title, String content) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (content == null || content.isBlank()) {
            return "(내용 없음)";
        }
        String firstLine = content.lines()
                .map(line -> line.replaceAll("^[#>*\\\\-\\\\s]+", "").trim())
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse(content.trim());
        String plain = firstLine.replaceAll("[*`_~]", "").trim();
        if (plain.isEmpty()) {
            return "(내용 없음)";
        }
        return plain.length() > 80 ? plain.substring(0, 80) : plain;
    }
}
