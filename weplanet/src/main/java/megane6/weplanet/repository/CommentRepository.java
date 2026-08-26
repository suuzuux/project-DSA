package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// JpaRepository<Comment, Long>만 상속받으면 save(), findById(), delete() 같은 기본 기능은
// 스프링이 알아서 다 만들어줌. 아래처럼 이름 규칙(findBy + 필드명 + 조건)에 맞춰 메서드만 선언하면
// 쿼리(SQL)도 스프링이 메서드 이름을 해석해서 자동으로 만들어줌 - 우리가 직접 SQL을 안 짜도 됨
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 특정 게시글(post)의 댓글을, 작성일(createdAt) 오래된 순(Asc)으로 조회
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);

    // 게시글 목록에서 댓글 개수만 필요할 때 (와이어프레임: 댓글 0개면 숫자 자체를 표시 안 함)
    long countByPost(Post post);

    // 게시글 삭제 시 그 게시글에 달린 댓글을 먼저 지우기 위함 (외래키 제약 때문에 순서가 중요함)
    void deleteByPost(Post post);

    // 하이라이트 "Comments by 아티스트" 위젯용 - 특정 유저(아티스트)가 작성한 댓글 중 최신 4개
    List<Comment> findTop4ByAuthorOrderByCreatedAtDesc(User author);

    // 내 프로필 "댓글 히스토리" 탭 - 내가 쓴 댓글 전체를 최신순/오래된순으로
    List<Comment> findByAuthorOrderByCreatedAtDesc(User author);
    List<Comment> findByAuthorOrderByCreatedAtAsc(User author);
}
