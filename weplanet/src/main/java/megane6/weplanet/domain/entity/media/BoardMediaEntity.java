package megane6.weplanet.domain.entity.media;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 미디어 게시글 (첨부파일 1:N)
@Entity
@Table(name = "board_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardMediaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;            // 커뮤니티(아티스트 그룹) id

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;         // 업로더(소속사) users.id

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 값이 있으면 삭제된 글(소프트 삭제)

    @Builder.Default
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Builder.Default
    @Column(name = "membership_only", nullable = false)
    private boolean membershipOnly = false;

    // 첨부파일 목록 (함께 저장·삭제, sort_order 순).
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    @Builder.Default
    private List<BoardMediaFileEntity> files = new ArrayList<>();

    // 양방향 연관관계를 함께 설정한다.
    public void addFile(BoardMediaFileEntity file) {
        this.files.add(file);
        file.setBoard(this);
    }
}
