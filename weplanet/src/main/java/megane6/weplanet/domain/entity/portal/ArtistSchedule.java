package megane6.weplanet.domain.entity.portal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ScheduleCategory;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ScheduleCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String location;

    @Column(name = "ticket_url", length = 500)
    private String ticketUrl;

    @Column(name = "schedule_at", nullable = false)
    private LocalDateTime scheduleAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ArtistSchedule(User artist, ScheduleCategory category, String title, String description,
                           String location, String ticketUrl, LocalDateTime scheduleAt) {
        this.artist = artist;
        this.category = category == null ? ScheduleCategory.OTHER : category;
        this.title = title.trim();
        this.description = blankToNull(description);
        this.location = blankToNull(location);
        this.ticketUrl = blankToNull(ticketUrl);
        this.scheduleAt = scheduleAt;
    }

    public static ArtistSchedule create(User artist, ScheduleCategory category, String title, String description,
                                        String location, String ticketUrl, LocalDateTime scheduleAt) {
        return new ArtistSchedule(artist, category, title, description, location, ticketUrl, scheduleAt);
    }

    public ScheduleCategory getCategory() {
        return category == null ? ScheduleCategory.OTHER : category;
    }

    public void update(ScheduleCategory category, String title, String description, String location,
                       String ticketUrl, LocalDateTime scheduleAt) {
        this.category = category == null ? ScheduleCategory.OTHER : category;
        this.title = title.trim();
        this.description = blankToNull(description);
        this.location = blankToNull(location);
        this.ticketUrl = blankToNull(ticketUrl);
        this.scheduleAt = scheduleAt;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.category == null) {
            this.category = ScheduleCategory.OTHER;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
