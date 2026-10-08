package megane6.weplanet.repository.comment;

import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

// 메서드 이름 규칙으로 쿼리를 자동 생성하는 댓글 리포지토리.
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 게시글 댓글 (오래된 순)
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);

    // 게시글 댓글 수
    long countByPost(Post post);

    // 게시글 삭제 전 댓글 삭제 (FK 순서)
    void deleteByPost(Post post);

    // 원댓글 삭제 시 답글이 남아 있는지 확인
    long countByParent(Comment parent);

    // 게시글 삭제 시 답글부터 지운다 (자기참조 FK).
    void deleteByPostAndParentIsNotNull(Post post);

    // Comments by 위젯 - 이 커뮤니티의 아티스트 쪽 계정 댓글·답글 최신 6개 (삭제 제외)
    List<Comment> findTop6ByAuthor_IdInAndPost_Artist_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
            Collection<Long> authorIds, Long artistId);

    // 내 프로필 댓글 이력 (최신순·오래된순)
    List<Comment> findByAuthorOrderByCreatedAtDesc(User author);
    List<Comment> findByAuthorOrderByCreatedAtAsc(User author);

    // 알림: 내가 쓴 글에 달린 댓글(본인 댓글 제외) 최신순
    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.post p
            JOIN FETCH c.author a
            LEFT JOIN FETCH p.artist
            WHERE p.author = :postAuthor
              AND a <> :postAuthor
            ORDER BY c.createdAt DESC
            """)
    List<Comment> findRecentOnMyPosts(@Param("postAuthor") User postAuthor);
    
    // [배지] 이 커뮤니티 글에 내가 쓴 댓글 수
    long countByAuthor_IdAndPost_Artist_Id(Long authorId, Long artistId);
}
