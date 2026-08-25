package megane6.weplanet.domain.dto.media;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardMediaFileViewDTO {

    private Long id;
    private String mediaType;
    private String originalName;
    private String contentType;
}
