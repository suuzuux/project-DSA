package megane6.weplanet.controller.live;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.live.LiveStatusView;
import megane6.weplanet.service.live.LiveBroadcastService;
import megane6.weplanet.service.live.LiveConnectionRegistry;
import megane6.weplanet.service.live.LiveRealtimePublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 호스트 웹소켓이 끊기면 잠깐 기다렸다가(재연결 여유) 아직 호스트가 없으면 방송을 종료한다.
 * 시청자가 끊기면 호스트에게 leave만 알린다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiveDisconnectListener {

	private static final long HOST_GRACE_SECONDS = 5;

	private final LiveConnectionRegistry liveConnectionRegistry;
	private final LiveBroadcastService liveBroadcastService;
	private final LiveRealtimePublisher liveRealtimePublisher;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final ConcurrentHashMap<Long, ScheduledFuture<?>> pendingHostEnds = new ConcurrentHashMap<>();

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		String sessionId = event.getSessionId();
		Long hostArtistId = liveConnectionRegistry.removeHostSession(sessionId);
		if (hostArtistId != null) {
			scheduleHostEnd(hostArtistId);
			return;
		}
		LiveConnectionRegistry.ViewerBinding viewer = liveConnectionRegistry.removeViewerSession(sessionId);
		if (viewer != null) {
			liveRealtimePublisher.notifyHost(viewer.artistId(), liveRealtimePublisher.leavePayload(viewer.userId()));
		}
	}

	public void cancelHostEnd(Long artistId) {
		if (artistId == null) {
			return;
		}
		ScheduledFuture<?> previous = pendingHostEnds.remove(artistId);
		if (previous != null) {
			previous.cancel(false);
		}
	}

	private void scheduleHostEnd(Long artistId) {
		ScheduledFuture<?> previous = pendingHostEnds.remove(artistId);
		if (previous != null) {
			previous.cancel(false);
		}
		ScheduledFuture<?> future = scheduler.schedule(() -> {
			pendingHostEnds.remove(artistId);
			if (liveConnectionRegistry.isHostConnected(artistId)) {
				return;
			}
			try {
				boolean ended = liveBroadcastService.endIfLive(artistId);
				if (ended) {
					liveRealtimePublisher.publishStatus(LiveStatusView.offline(), artistId);
					log.info("호스트 연결 종료로 라이브를 종료함 artistId={}", artistId);
				}
			} catch (RuntimeException e) {
				log.warn("호스트 disconnect 후 라이브 종료 실패 artistId={}: {}", artistId, e.getMessage());
			}
		}, HOST_GRACE_SECONDS, TimeUnit.SECONDS);
		pendingHostEnds.put(artistId, future);
	}

	@PreDestroy
	public void shutdown() {
		pendingHostEnds.values().forEach(future -> future.cancel(false));
		scheduler.shutdownNow();
	}
}
