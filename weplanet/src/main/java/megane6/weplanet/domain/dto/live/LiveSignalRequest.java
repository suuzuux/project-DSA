package megane6.weplanet.domain.dto.live;

import lombok.Data;

@Data
public class LiveSignalRequest {
	private Long artistId;
	private Long toUserId;
	private String type;
	private String sdp;
	private String candidate;
	private String sdpMid;
	private Integer sdpMLineIndex;
}
