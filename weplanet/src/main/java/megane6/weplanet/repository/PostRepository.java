package megane6.weplanet.repository;

import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 게시판 종류별 최신순 (레거시)
    List<Post> findByBoardTypeOrderByCreatedAtDesc(BoardType boardType);

    // 게시판 종류별 인기순 (레거시)
    List<Post> findByBoardTypeOrderByLikeCountDescCreatedAtDesc(BoardType boardType);

    // 커뮤니티별 게시판 목록 - 최신순
    List<Post> findByBoardTypeAndArtistOrderByCreatedAtDesc(BoardType boardType, User artist);

    // 커뮤니티별 게시판 목록 - 인기순
    List<Post> findByBoardTypeAndArtistOrderByLikeCountDescCreatedAtDesc(BoardType boardType, User artist);

    // 최신 인기 포스트 - 아티스트 게시판, 숨김 제외, 인기순 4개
    List<Post> findTop4ByBoardTypeAndHiddenFromArtistFalseAndArtistIsNotNullOrderByLikeCountDescCreatedAtDesc(BoardType boardType);

    // 커뮤니티 게시판 10개 단위 Slice 조회
    @EntityGraph(attributePaths = {"author", "artist"})
    Slice<Post> findByBoardTypeAndArtist(BoardType boardType, User artist, Pageable pageable);

    // 사진/미디어 필터 - 사진·영상 첨부가 있는 글만 (MIME 이 애매하면 확장자로 판별).
    @EntityGraph(attributePaths = {"author", "artist"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.boardType = :boardType
              AND p.artist = :artist
              AND EXISTS (
                  SELECT 1 FROM PostAttachment a
                  WHERE a.post = p
                    AND (a.contentType LIKE 'image/%' OR a.contentType LIKE 'video/%'
                         OR LOWER(a.storedName) LIKE '%.png' OR LOWER(a.storedName) LIKE '%.jpg'
                         OR LOWER(a.storedName) LIKE '%.jpeg' OR LOWER(a.storedName) LIKE '%.gif'
                         OR LOWER(a.storedName) LIKE '%.webp' OR LOWER(a.storedName) LIKE '%.bmp'
                         OR LOWER(a.storedName) LIKE '%.mp4' OR LOWER(a.storedName) LIKE '%.webm'
                         OR LOWER(a.storedName) LIKE '%.mov')
              )
            """)
    Slice<Post> findMediaPostsByBoardTypeAndArtist(
            @Param("boardType") BoardType boardType,
            @Param("artist") User artist,
            Pageable pageable);

    // Hide from Artists 글 제외 조회
    @EntityGraph(attributePaths = {"author", "artist"})
    Slice<Post> findByBoardTypeAndArtistAndHiddenFromArtistFalse(
            BoardType boardType, User artist, Pageable pageable);

    // 하이라이트 위젯용 최신 글 6개
    List<Post> findTop6ByBoardTypeAndArtistOrderByCreatedAtDesc(BoardType boardType, User artist);

    List<Post> findTop20ByBoardTypeAndArtist_IdInOrderByCreatedAtDesc(BoardType boardType,
                                                                        Collection<Long> artistIds);

    // 내 프로필 포스트 이력 (최신순·오래된순)
    List<Post> findByAuthorOrderByCreatedAtDesc(User author);
    List<Post> findByAuthorOrderByCreatedAtAsc(User author);
    
    @Query("""
        select new megane6.weplanet.domain.dto.ArtistCount(
            post.artist.id,
            count(post)
        )
        from Post post
        where post.artist.id in :artistIds
        group by post.artist.id
        """)
    List<ArtistCount> countPostsByArtistIds(
            @Param("artistIds") Collection<Long> artistIds
    );
    
    // 팬·아티스트 게시글 최신 10개
    @EntityGraph(attributePaths = "author")
    List<Post> findTop10ByArtistOrderByCreatedAtDesc(User artist);
    
    // [배지] 이 커뮤니티 팬 게시판에 글을 쓴 적이 있는지
    boolean existsByAuthor_IdAndArtist_IdAndBoardType(
            Long authorId, Long artistId, BoardType boardType
    );
    
    // [배지] 이 커뮤니티 내 글이 받은 좋아요 합계 (없으면 0)
    @Query("""
        SELECT COALESCE(SUM(p.likeCount), 0)
        FROM Post p
        WHERE p.author.id = :authorId
        AND p.artist.id = :artistId
        """)
    long sumLikeCountByAuthorAndArtist(@Param("authorId") Long authorId,
                                       @Param("artistId") Long artistId);
}
