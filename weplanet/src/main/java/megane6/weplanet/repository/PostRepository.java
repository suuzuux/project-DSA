package megane6.weplanet.repository;

import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 게시판 종류(팬/아티스트)별로 게시글 목록 조회 - 최신순 (레거시 /posts 경로)
    List<Post> findByBoardTypeOrderByCreatedAtDesc(BoardType boardType);

    // 게시판 종류별로 게시글 목록 조회 - 인기순 (레거시 /posts 경로)
    List<Post> findByBoardTypeOrderByLikeCountDescCreatedAtDesc(BoardType boardType);

    // 커뮤니티별 게시판 목록 - 최신순
    List<Post> findByBoardTypeAndArtistOrderByCreatedAtDesc(BoardType boardType, User artist);

    // 커뮤니티별 게시판 목록 - 인기순
    List<Post> findByBoardTypeAndArtistOrderByLikeCountDescCreatedAtDesc(BoardType boardType, User artist);

    // 메인 페이지 "최신 인기 포스트" 위젯용 - 게시판 종류 구분 없이 전체에서 인기순 상위 4개
    List<Post> findTop4ByOrderByLikeCountDescCreatedAtDesc();

    // 하이라이트 "Fan Posts" 위젯용 - 특정 커뮤니티의 최신 게시글 상위 4개
    List<Post> findTop4ByBoardTypeAndArtistOrderByCreatedAtDesc(BoardType boardType, User artist);

    List<Post> findTop20ByBoardTypeAndArtist_IdInOrderByCreatedAtDesc(BoardType boardType,
                                                                        Collection<Long> artistIds);

    // 내 프로필 "포스트 히스토리" 탭 - 내가 쓴 게시글 전체를 최신순/오래된순으로
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
    
    // 팬 + 아티스트 게시글 합쳐 최신 10개 호출
    @EntityGraph(attributePaths = "author")
    List<Post> findTop10ByArtistOrderByCreatedAtDesc(User artist);
    
    // [배지] 이 커뮤니티 팬 게시판에 글을 쓴 적이 있는지 (첫 게시글 배지)
    boolean existsByAuthor_IdAndArtist_IdAndBoardType(
            Long authorId, Long artistId, BoardType boardType
    );
    
    // [배지] 이 커뮤니티에 쓴 내 글들이 받은 좋아요 합계 (받은 좋아요 배지)
    // 글이 하나도 없으면 SUM 결과가 null 이라 coalesce로 0 처리
    @Query("""
        SELECT COALESCE(SUM(p.likeCount), 0)
        FROM Post p
        WHERE p.author.id = :authorId
        AND p.artist.id = :artistId
        """)
    long sumLikeCountByAuthorAndArtist(@Param("authorId") Long authorId,
                                       @Param("artistId") Long artistId);
}
