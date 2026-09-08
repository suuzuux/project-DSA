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
    private int likeCount;
    private List<BoardMediaFileViewDTO> files;

    public boolean isLiveReplay() {
        return title != null && title.startsWith("라이브 다시보기");
    }

    public Long getFirstVideoFileId() {
        if (files == null || files.isEmpty()) {
            return null;
        }
        return files.stream()
                .filter(file -> file.getMediaType() != null && "VIDEO".equalsIgnoreCase(file.getMediaType()))
                .map(BoardMediaFileViewDTO::getId)
                .findFirst()
                .orElse(files.get(0).getId());
    }
}
