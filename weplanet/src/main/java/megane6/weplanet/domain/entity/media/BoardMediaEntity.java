package megane6.weplanet.domain.entity.media;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 미디어 게시글 (board_media 테이블). 첨부파일과 1:N 관계.
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

    // 첨부파일 목록. 게시글을 저장하면 파일도 같이 저장(cascade),
    // 목록에서 빼면 그 파일 행도 삭제(orphanRemoval), sort_order 순으로 정렬.
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    @Builder.Default
    private List<BoardMediaFileEntity> files = new ArrayList<>();

    // 파일을 게시글에 붙이는 헬퍼 (양방향 연결을 안전하게 설정)
    public void addFile(BoardMediaFileEntity file) {
        this.files.add(file);
        file.setBoard(this);
    }
}
