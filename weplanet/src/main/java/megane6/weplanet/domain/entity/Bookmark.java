package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * "누가 어떤 게시글을 북마크(저장)했는지"를 기록하는 엔티티.
 * 좋아요(Like.java)와 완전히 같은 구조 - uniqueConstraints로 같은 사람이
 * 같은 글을 두 번 북마크하는 것을 DB 차원에서 막아둠.
 */
@Entity
@Table(
        name = "post_bookmark",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
