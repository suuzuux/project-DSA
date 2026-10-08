package megane6.weplanet.domain.entity.media;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 미디어 게시글 첨부 파일
@Entity
@Table(name = "board_media_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardMediaFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 첨부된 게시글
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private BoardMediaEntity board;

    @Column(name = "original_name")
    private String originalName;      // 원본 파일명

    @Column(name = "stored_name", nullable = false)
    private String storedName;        // 저장된 파일명(UUID 등)

    @Column(name = "content_type")
    private String contentType;       // MIME (image/png ...)

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;         // "IMAGE" 또는 "VIDEO"

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;        // 게시글 안에서의 표시 순서(0부터)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
