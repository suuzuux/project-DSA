package megane6.weplanet.domain.entity.portal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortalNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "pin_order")
    private Integer pinOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PortalNotice(User artist, String title, String content, boolean published) {
        this.artist = artist;
        this.title = title;
        this.content = content;
        this.published = published;
    }

    public static PortalNotice create(User artist, String title, String content, boolean published) {
        return new PortalNotice(artist, title, content, published);
    }

    public void update(String title, String content, boolean published) {
        this.title = title.trim();
        this.content = content.trim();
        this.published = published;
    }

    public void applyPin(boolean pinned, Integer pinOrder) {
        this.pinned = pinned;
        this.pinOrder = pinOrder;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
