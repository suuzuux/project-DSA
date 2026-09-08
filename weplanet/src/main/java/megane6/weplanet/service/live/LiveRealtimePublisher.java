package megane6.weplanet.service.live;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.live.LiveCommentView;
import megane6.weplanet.domain.dto.live.LiveStatusView;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LiveRealtimePublisher {

	private final SimpMessagingTemplate messagingTemplate;

	private void broadcast(String destination, Map<String, Object> payload) {
		messagingTemplate.convertAndSend(destination, (Object) payload);
	}

	public void publishStatus(LiveStatusView status, Long artistId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", status.live() ? "LIVE" : "ENDED");
		payload.put("live", status.live());
		payload.put("sessionId", status.sessionId());
		payload.put("artistId", artistId);
		payload.put("hostUserId", status.hostUserId());
		payload.put("startedAt", status.startedAt());
		broadcast("/topic/live." + artistId + ".status", payload);
	}

	public void notifyHost(Long artistId, Map<String, Object> payload) {
		broadcast("/topic/live." + artistId + ".host", payload);
	}

	public void sendToPeer(Long artistId, Long userId, Map<String, Object> payload) {
		broadcast("/topic/live." + artistId + ".peer." + userId, payload);
	}

	public void publishComment(Long artistId, LiveCommentView comment) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("id", comment.id());
		payload.put("authorId", comment.authorId());
		payload.put("authorNickname", comment.authorNickname());
		payload.put("content", comment.content());
		payload.put("createdAt", comment.createdAt());
		payload.put("fromArtist", comment.fromArtist());
		broadcast("/topic/live." + artistId + ".comments", payload);
	}

	public void sendError(Long userId, String message) {
		broadcast("/topic/live.error." + userId, Map.of(
				"error", true,
				"message", message
		));
	}

	public Map<String, Object> joinPayload(Long viewerId, String nickname) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", "join");
		payload.put("viewerId", viewerId);
		payload.put("nickname", nickname);
		return payload;
	}

	public Map<String, Object> leavePayload(Long viewerId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", "leave");
		payload.put("viewerId", viewerId);
		return payload;
	}
}
