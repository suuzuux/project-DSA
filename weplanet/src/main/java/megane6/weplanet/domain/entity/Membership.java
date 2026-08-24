package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * "어떤 팬이 어떤 아티스트의 멤버십을 언제까지 구독 중인지"를 기록하는 엔티티.
 * <p>
 * 와이어프레임 19번(DM 구독 만료 배너)을 보여주기 위한 최소한의 구조만 만들어둠.
 * 실제 결제/가입 화면, 멤버십 상품 관리 같은 건 이 팀(CHAT)의 담당이 아니라서 안 만들었고,
 * "만료 여부를 어떻게 판단하는지"만 다른 팀원이 나중에 실제 멤버십 기능을 붙일 때 참고할 수 있게 해둠.
 */
@Entity
@Table(
        name = "membership",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fan_id", "artist_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fan_id", nullable = false)
    private User fan;

    @ManyToOne
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    // 이 시점이 지나면 만료된 것으로 봄 (와이어프레임: "DM 구독 만료" 배너 표시 기준)
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
