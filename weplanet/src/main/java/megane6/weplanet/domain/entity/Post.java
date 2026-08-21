package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    // 이 게시글이 팬 게시판 소유인지, 아티스트 게시판 소유인지
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardType boardType;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 인기순 정렬용 좋아요 개수 (FEED-05에서 좋아요 기능이 이 값을 올리고 내림)
    @Builder.Default
    @Column(nullable = false)
    private int likeCount = 0;

    // 엔티티가 저장되기 직전에 자동으로 실행됨
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}

