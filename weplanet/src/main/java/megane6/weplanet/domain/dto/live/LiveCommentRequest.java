package megane6.weplanet.domain.dto.live;

import lombok.Data;

@Data
public class LiveCommentRequest {
	private Long artistId;
	private String content;
}
