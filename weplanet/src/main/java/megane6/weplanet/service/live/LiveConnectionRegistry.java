package megane6.weplanet.service.live;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 라이브 호스트/시청자의 STOMP 세션을 기억해, 연결이 끊기면 상대에게 leave를 알리거나
 * 호스트 퇴장 시 방송을 종료하기 위한 레지스트리.
 */
@Component
public class LiveConnectionRegistry {

	public record ViewerBinding(long artistId, long userId) {}

	private final ConcurrentHashMap<String, Long> hostSessionToArtist = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, String> artistToHostSession = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, ViewerBinding> viewerSessionToBinding = new ConcurrentHashMap<>();

	public void registerHost(String sessionId, long artistId) {
		if (sessionId == null) {
			return;
		}
		String previous = artistToHostSession.put(artistId, sessionId);
		if (previous != null && !previous.equals(sessionId)) {
			hostSessionToArtist.remove(previous);
		}
		hostSessionToArtist.put(sessionId, artistId);
	}

	public Long removeHostSession(String sessionId) {
		if (sessionId == null) {
			return null;
		}
		Long artistId = hostSessionToArtist.remove(sessionId);
		if (artistId != null) {
			artistToHostSession.remove(artistId, sessionId);
		}
		return artistId;
	}

	public boolean isHostConnected(long artistId) {
		return artistToHostSession.containsKey(artistId);
	}

	public void registerViewer(String sessionId, long artistId, long userId) {
		if (sessionId == null) {
			return;
		}
		viewerSessionToBinding.put(sessionId, new ViewerBinding(artistId, userId));
	}

	public ViewerBinding removeViewerSession(String sessionId) {
		if (sessionId == null) {
			return null;
		}
		return viewerSessionToBinding.remove(sessionId);
	}

	public Long hostArtistOf(String sessionId) {
		return sessionId == null ? null : hostSessionToArtist.get(sessionId);
	}
}
