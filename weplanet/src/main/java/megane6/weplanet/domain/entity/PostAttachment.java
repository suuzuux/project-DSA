package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 게시글에 첨부된 파일(이미지든 일반파일이든 다) 하나
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

    // 사용자가 업로드할 때 쓴 원래 파일명 (화면 표시용)
    @Column(nullable = false, length = 255)
    private String originalName;

    // 서버에 실제로 저장할 때 쓰는 이름(UUID 기반) - 한글 깨짐/파일명 충돌 방지
    @Column(nullable = false, length = 255, unique = true)
    private String storedName;

    // image/png, application/pdf 등 - 이미지인지 아닌지 화면에서 구분할 때 사용
    @Column(length = 100)
    private String contentType;

    private Long fileSize;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // 화면에서 "이미지인지 아닌지" 바로 물어볼 수 있게
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
}
