package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 팬의 아티스트 멤버십 구독 기간. */
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

    // 만료 시각 (지나면 DM 구독 만료 배너 표시)
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
