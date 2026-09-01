package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "fan_project_cover_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, unique = true, length = 255)
    private String storedName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ProjectImage(
            Project project,
            String originalName,
            String storedName,
            String contentType,
            Long fileSize
    ) {
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("대표 이미지에는 이미지 파일만 등록할 수 있습니다.");
        }
        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException("비어 있는 이미지 파일은 등록할 수 없습니다.");
        }

        this.project = project;
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public static ProjectImage create(
            Project project,
            String originalName,
            String storedName,
            String contentType,
            Long fileSize
    ) {
        return new ProjectImage(project, originalName, storedName, contentType, fileSize);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
