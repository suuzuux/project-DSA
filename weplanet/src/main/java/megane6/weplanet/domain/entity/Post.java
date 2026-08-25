package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게시글 하나를 표현하는 엔티티(=DB의 post 테이블과 1:1로 매칭되는 자바 클래스).
 * <p>
 *
 * @Data : 롬복이 getter/setter/toString/equals를 자동으로 만들어줌 (직접 안 써도 됨)
 * @Builder : new Post(...) 대신 Post.builder().title("...").build() 처럼 이름표를 붙여서 객체를 만들 수 있게 해줌.
 * 필드가 많을 때 어떤 값이 어떤 필드인지 헷갈리지 않아서 좋음.
 */
@Entity
@Table(name = "post")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 게시글이 팬 게시판(FAN) 소속인지, 아티스트 게시판(ARTIST) 소속인지 구분
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardType boardType;

    // 어느 아티스트 커뮤니티에 속한 글인지 (users.id, role=ARTIST)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private User artist;

    @Column(nullable = false, length = 200)
    private String title;

    // @Lob + TEXT 컬럼 : 글자 수 제한(255자 등) 없이 긴 본문을 저장하기 위함
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 작성자 - User 테이블과 다대일(N:1) 관계. 게시글 여러 개가 유저 한 명을 가리킬 수 있음
    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 인기순 정렬에 쓰는 좋아요 개수. 좋아요를 누르거나 취소할 때마다 이 값을 +1/-1 해줌
    // (매번 좋아요 테이블 개수를 세는 대신, 미리 계산해둔 값을 여기에 저장해서 조회 속도를 빠르게 함)
    @Builder.Default
    @Column(nullable = false)
    private int likeCount = 0;

    // @PrePersist : 이 엔티티가 DB에 처음 저장되기 "직전"에 스프링이 자동으로 이 메서드를 실행해줌
    // 그래서 게시글 작성할 때 createdAt을 직접 안 넣어줘도 항상 현재 시각이 자동으로 채워짐
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
