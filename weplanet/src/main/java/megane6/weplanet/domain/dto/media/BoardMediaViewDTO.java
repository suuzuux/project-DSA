package megane6.weplanet.domain.dto.media;

import lombok.*;
import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;

import java.time.LocalDateTime;
import java.util.List;

// 화면에 게시글을 뿌릴 때 쓰는 읽기 전용 DTO.
// 게시글 정보 + 첨부파일 목록을 함께 담는다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardMediaViewDTO {

    private Long id;
    private Long groupId;
    private Long uploaderId;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private int fileCount;                       // 첨부 개수
    private List<BoardMediaFileEntity> files;    // 첨부파일들 (화면에서 img/video 로 표시)
}
