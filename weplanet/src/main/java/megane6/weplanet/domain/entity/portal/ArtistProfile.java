package megane6.weplanet.domain.entity.portal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "artist_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false, unique = true)
    private User artist;

    @Column(columnDefinition = "TEXT")
    private String intro;

    /** 배경 이미지: 업로드 저장 파일명 또는 외부 URL */
    @Column(name = "header_image_url", length = 500)
    private String headerImageUrl;

    /** 커뮤니티 프로필(로고) 사진: 업로드 저장 파일명 또는 외부 URL */
    @Column(name = "logo_image_url", length = 500)
    private String logoImageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ArtistProfile(User artist) {
        this.artist = artist;
    }

    public static ArtistProfile create(User artist) {
        return new ArtistProfile(artist);
    }

    public void updateIntro(String intro) {
        this.intro = blankToNull(intro);
    }

    public void replaceLogoImage(String storedNameOrUrl) {
        this.logoImageUrl = blankToNull(storedNameOrUrl);
    }

    public void clearLogoImage() {
        this.logoImageUrl = null;
    }

    public void replaceHeaderImage(String storedNameOrUrl) {
        this.headerImageUrl = blankToNull(storedNameOrUrl);
    }

    public void clearHeaderImage() {
        this.headerImageUrl = null;
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
