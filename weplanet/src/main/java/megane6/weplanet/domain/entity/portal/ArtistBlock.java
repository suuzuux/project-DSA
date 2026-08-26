package megane6.weplanet.domain.entity.portal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "artist_block",
        uniqueConstraints = @UniqueConstraint(columnNames = {"artist_id", "blocked_user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ArtistBlock(User artist, User blockedUser, String reason) {
        this.artist = artist;
        this.blockedUser = blockedUser;
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public static ArtistBlock create(User artist, User blockedUser, String reason) {
        return new ArtistBlock(artist, blockedUser, reason);
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
