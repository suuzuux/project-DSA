package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Bookmark;
import megane6.weplanet.domain.entity.Like;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.PostAttachment;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.BookmarkRepository;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.repository.LikeRepository;
import megane6.weplanet.repository.PostAttachmentRepository;
import megane6.weplanet.repository.PostRepository;
import megane6.weplanet.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * 게시글(Post)과 관련된 실제 처리 로직을 모아둔 서비스 클래스.
 * <p>
 * 컨트롤러(PostController)는 "요청을 받아서 어떤 서비스 메서드를 부를지"만 결정하고,
 * 실제 계산/검증/저장 같은 진짜 로직은 전부 여기(Service)에 모아둠.
 * 이렇게 나누면 나중에 컨트롤러 종류(웹 화면, API 등)가 바뀌어도 이 로직은 그대로 재사용할 수 있음.
 */
@Service
@RequiredArgsConstructor // final로 선언된 필드들을 파라미터로 받는 생성자를 롬복이 자동으로 만들어줌 (의존성 주입)
public class PostService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final CommentReportRepository commentReportRepository;
    private final PostAttachmentRepository postAttachmentRepository;
    private final FileStorageService fileStorageService;

    // 게시판 종류별 목록 조회 - sort 값에 따라 최신순/인기순 중 하나를 골라서 리포지토리에 위임
    public List<Post> getPostsByBoardType(BoardType boardType, String sort) {
        if ("popular".equals(sort)) {
            return postRepository.findByBoardTypeOrderByLikeCountDescCreatedAtDesc(boardType);
        }
        return postRepository.findByBoardTypeOrderByCreatedAtDesc(boardType);
    }

    // 메인 페이지 "최신 인기 포스트" 위젯용 - 게시판 구분 없이 전체 인기 게시글 상위 4개
    public List<Post> getPopularPosts() {
        return postRepository.findTop4ByOrderByLikeCountDescCreatedAtDesc();
    }

    // 하이라이트 "Fan Posts" 위젯용 - 특정 게시판의 최신 게시글 상위 4개
    public List<Post> getRecentPosts(BoardType boardType) {
        return postRepository.findTop4ByBoardTypeOrderByCreatedAtDesc(boardType);
    }

    // 게시글 작성 - Post 객체를 만들어서 DB에 저장하고, 저장된(id가 채워진) 결과를 돌려줌
    public Post createPost(BoardType boardType, String title, String content, User author) {
        return createPost(boardType, title, content, author, null, false);
    }

    // 36번: 팬 게시판 글쓰기 모달의 🔗 링크 첨부, "Hide from Artists" 토글까지 받는 오버로드
    public Post createPost(BoardType boardType, String title, String content, User author,
                            String linkUrl, boolean hiddenFromArtist) {
        Post post = Post.builder()
                .boardType(boardType)
                .title(title)
                .content(content)
                .author(author)
                .linkUrl(linkUrl)
                .hiddenFromArtist(hiddenFromArtist)
                .build();

        return postRepository.save(post);
    }

    /**
     * 게시글에 첨부파일 여러 개를 저장함.
     * <p>
     * MultipartFile : 사용자가 <input type="file"> 로 올린 파일 하나를 자바 코드에서 다루기 위한 타입.
     * 여기서 하는 일은 두 단계임.
     * 1) fileStorageService.store(file) 로 실제 파일 내용을 서버 컴퓨터의 디스크(uploads 폴더)에 저장
     * 2) 그 파일의 "이름표"(원래 파일명, 저장된 파일명, 용량 등)만 DB(post_attachment 테이블)에 기록
     * 즉 파일의 실제 내용물은 디스크에, 파일에 대한 정보는 DB에 나눠서 보관하는 구조.
     */
    public void saveAttachments(Post post, List<MultipartFile> files) {
        if (files == null) {
            return;
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue; // 사용자가 파일을 선택하지 않은 빈 input은 건너뜀
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

    // 게시글에 달린 첨부파일 목록 조회
    public List<PostAttachment> getAttachments(Post post) {
        return postAttachmentRepository.findByPostOrderByIdAsc(post);
    }

    // 게시글 상세 조회 - Optional<Post> 상태로 찾아보고, 없으면 예외를 던짐.
    // orElseThrow(...) : 값이 있으면(Optional이 비어있지 않으면) 그 값을 그대로 꺼내주고,
    //                    없으면 괄호 안의 예외를 만들어서 던져버림 (null을 그냥 돌려주는 것보다 안전함)
    public Post getPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다. id=" + postId));
    }

    // 좋아요 토글 - 이미 눌려 있으면 취소하고, 안 눌려 있으면 새로 누름 (같은 버튼이 두 가지 역할을 함)
    // Post.likeCount 값도 같이 +1/-1 해줘서, 매번 좋아요 개수를 세지 않고도 빠르게 조회할 수 있게 함
    public boolean toggleLike(Post post, User user) {
        Optional<Like> existing = likeRepository.findByPostAndUser(post, user);

        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            post.setLikeCount(post.getLikeCount() - 1);
            postRepository.save(post);
            return false; // 좋아요가 취소됐음을 컨트롤러에 알려줌
        } else {
            Like like = Like.builder()
                    .post(post)
                    .user(user)
                    .build();
            likeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);
            return true; // 좋아요가 새로 눌렸음을 컨트롤러에 알려줌
        }
    }

    // 북마크 토글 - 좋아요 토글과 완전히 같은 방식. 단, 게시글에 별도 카운트 컬럼을 안 두고
    // 매번 실제 개수를 세는 방식(getBookmarkCount)으로 처리함 (좋아요보다 조회 빈도가 낮을 거라 판단)
    public boolean toggleBookmark(Post post, User user) {
        Optional<Bookmark> existing = bookmarkRepository.findByPostAndUser(post, user);

        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            return false; // 북마크가 취소됐음을 컨트롤러에 알려줌
        } else {
            Bookmark bookmark = Bookmark.builder()
                    .post(post)
                    .user(user)
                    .build();
            bookmarkRepository.save(bookmark);
            return true; // 북마크가 새로 눌렸음을 컨트롤러에 알려줌
        }
    }

    // 지금 이 유저가 이 게시글을 북마크해뒀는지 여부 (상세 화면 진입 시 아이콘 채워진 상태로 보여주기 위함)
    public boolean isBookmarked(Post post, User user) {
        return bookmarkRepository.findByPostAndUser(post, user).isPresent();
    }

    /**
     * 게시글 삭제 - 작성자 본인만 삭제 가능.
     * <p>
     *
     * @Transactional : 이 메서드 안에서 실행되는 여러 개의 DB 작업(삭제 5번)을 하나의 묶음으로 처리함.
     * 만약 중간에 하나라도 실패하면, 이미 실행됐던 나머지 삭제들도 전부 취소(롤백)되고 원래 상태로 되돌아감.
     * (예: 좋아요는 지웠는데 댓글 지우다가 에러나서 게시글은 안 지워지는 "어중간한 상태"를 방지함)
     */
    @Transactional
    public void deletePost(Post post, User requester) {
        if (!post.getAuthor().getId().equals(requester.getId())) {
            throw new IllegalStateException("본인이 작성한 게시글만 삭제할 수 있습니다.");
        }

        // 첨부파일은 DB 기록을 지우기 전에, 디스크에 실제로 저장된 파일부터 먼저 지움
        List<PostAttachment> attachments = postAttachmentRepository.findByPostOrderByIdAsc(post);
        for (PostAttachment attachment : attachments) {
            fileStorageService.delete(attachment.getStoredName());
        }

        // 게시글(post)을 외래키로 참조하고 있는 데이터들을 먼저 지운 뒤에 게시글 자체를 삭제해야 함
        // (참조하는 자식 데이터가 남아있는 상태로 부모(게시글)를 먼저 지우면 외래키 제약 위반 에러가 남)
        // 댓글의 신고 기록(comment_report)은 댓글(comment)보다 먼저 지워야 하므로 순서가 중요함
        commentReportRepository.deleteByComment_Post(post);
        likeRepository.deleteByPost(post);
        bookmarkRepository.deleteByPost(post);
        commentRepository.deleteByPost(post);
        reportRepository.deleteByPost(post);
        postAttachmentRepository.deleteByPost(post);
        postRepository.delete(post);
    }

    // 게시글 수정 - 작성자 본인 또는 관리자만 가능
    public void updatePost(Post post, String title, String content, User requester) {
        boolean isAuthor = post.getAuthor().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("본인이 작성한 게시글 또는 관리자만 수정할 수 있습니다.");
        }

        // post 객체의 값만 바꿔주면, 트랜잭션이 끝날 때 JPA(Hibernate)가 알아서 변경된 부분만 UPDATE 쿼리로 반영해줌
        // (이런 방식을 "더티 체킹"이라고 부름 - 굳이 save()를 다시 안 불러도 됨. 여기선 명확하게 save도 호출함)
        post.setTitle(title);
        post.setContent(content);
        postRepository.save(post);
    }
}
