package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * "누가 어떤 게시글에 좋아요를 눌렀는지"를 기록하는 엔티티.
 * <p>
 * uniqueConstraints : (post_id, user_id) 조합이 DB에 딱 하나만 존재하도록 강제함.
 * 즉 "같은 사람이 같은 글에 좋아요를 두 번 누르는 것"을 DB 차원에서 원천 차단함
 * (자바 코드에서 실수로 막는 걸 깜빡해도, DB가 알아서 막아준다는 뜻 - 이중 안전장치).
 */
@Entity
@Table(
        name = "post_like",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Like {

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
