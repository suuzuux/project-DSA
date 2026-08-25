package megane6.weplanet.domain.entity.portal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "artist_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String location;

    @Column(name = "schedule_at", nullable = false)
    private LocalDateTime scheduleAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ArtistSchedule(User artist, String title, String description, String location, LocalDateTime scheduleAt) {
        this.artist = artist;
        this.title = title;
        this.description = description;
        this.location = location;
        this.scheduleAt = scheduleAt;
    }

    public static ArtistSchedule create(User artist, String title, String description, String location, LocalDateTime scheduleAt) {
        return new ArtistSchedule(artist, title.trim(), blankToNull(description), blankToNull(location), scheduleAt);
    }

    public void update(String title, String description, String location, LocalDateTime scheduleAt) {
        this.title = title.trim();
        this.description = blankToNull(description);
        this.location = blankToNull(location);
        this.scheduleAt = scheduleAt;
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
