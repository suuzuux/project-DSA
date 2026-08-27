package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.FanBadgeType;

import java.time.LocalDateTime;

@Entity
@Table(name = "fan_badge_ownership")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FanBadgeOwnership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fan_id", nullable = false)
    private User fan;

    // 팀 공통 커뮤니티가 users.id(ARTIST)를 기준으로 동작하므로 같은 아티스트를 참조한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    @Column(name = "badge_code", nullable = false, length = 50)
    private String badgeCode;

    @Column(name = "badge_name", nullable = false, length = 100)
    private String badgeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false, length = 20)
    private FanBadgeType badgeType;

    @Column(name = "awarded_at", nullable = false, updatable = false)
    private LocalDateTime awardedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    // 시스템 자동 지급이면 null, 관리자가 지급하면 해당 users.id가 저장된다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "awarded_by")
    private User awardedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FanBadgeOwnership(
            User fan,
            User artist,
            String badgeCode,
            String badgeName,
            FanBadgeType badgeType,
            User awardedBy
    ) {
        if (fan == null || artist == null || badgeType == null) {
            throw new IllegalArgumentException("팬, 아티스트, 뱃지 유형은 필수입니다.");
        }
        if (badgeCode == null || badgeCode.isBlank()) {
            throw new IllegalArgumentException("뱃지 코드는 필수입니다.");
        }
        if (badgeName == null || badgeName.isBlank()) {
            throw new IllegalArgumentException("뱃지 이름은 필수입니다.");
        }

        this.fan = fan;
        this.artist = artist;
        this.badgeCode = badgeCode;
        this.badgeName = badgeName;
        this.badgeType = badgeType;
        this.awardedBy = awardedBy;
    }

    public static FanBadgeOwnership award(
            User fan,
            User artist,
            String badgeCode,
            String badgeName,
            FanBadgeType badgeType,
            User awardedBy
    ) {
        return new FanBadgeOwnership(
                fan,
                artist,
                badgeCode,
                badgeName,
                badgeType,
                awardedBy
        );
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
        }
    }

    public boolean isActive() {
        return this.revokedAt == null;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.awardedAt = now;
        this.createdAt = now;
    }
}
