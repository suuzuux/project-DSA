package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 게시글 첨부 파일
@Entity
@Table(name = "post_attachment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 업로드한 원래 파일명
    @Column(nullable = false, length = 255)
    private String originalName;

    // 서버 저장 파일명 (UUID 기반)
    @Column(nullable = false, length = 255, unique = true)
    private String storedName;

    // MIME 타입
    @Column(length = 100)
    private String contentType;

    private Long fileSize;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // 이미지인지 판별 (MIME 이 애매하면 확장자로 확인).
    public boolean isImage() {
        if (contentType != null && contentType.startsWith("image/")) {
            return true;
        }
        return hasImageExtension();
    }

    private boolean hasImageExtension() {
        String name = originalName != null ? originalName : storedName;
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp");
    }
}
