package megane6.weplanet.domain.dto.media;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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
    private int fileCount;
    private List<BoardMediaFileViewDTO> files;
}
