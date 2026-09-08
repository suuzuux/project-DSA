package megane6.weplanet.controller.live;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.live.LiveCommentRequest;
import megane6.weplanet.domain.dto.live.LiveCommentView;
import megane6.weplanet.domain.dto.live.LiveJoinRequest;
import megane6.weplanet.domain.dto.live.LiveSignalRequest;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.live.LiveSession;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.live.LiveBroadcastService;
import megane6.weplanet.service.live.LiveConnectionRegistry;
import megane6.weplanet.service.live.LiveRealtimePublisher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LiveStompController {

	private static final Set<String> SIGNAL_TYPES = Set.of("offer", "answer", "candidate");

	private final LiveBroadcastService liveBroadcastService;
	private final LiveRealtimePublisher liveRealtimePublisher;
	private final LiveConnectionRegistry liveConnectionRegistry;
	private final LiveDisconnectListener liveDisconnectListener;
	private final UserRepository userRepository;
	private final CommunityJoinService communityJoinService;

	@MessageMapping("/live.host")
	public void hostReady(LiveJoinRequest request, Authentication authentication, SimpMessageHeaderAccessor headers) {
		AuthenticatedUser me = principalOf(authentication);
		if (me == null || request == null || request.getArtistId() == null) {
			return;
		}
		try {
			User host = userRepository.findById(me.getId()).orElseThrow();
			LiveSession session = liveBroadcastService.requireLive(request.getArtistId());
			if (!session.isHost(host)) {
				liveRealtimePublisher.sendError(me.getId(), "이 방송의 호스트가 아닙니다.");
				return;
			}
			liveConnectionRegistry.registerHost(headers.getSessionId(), request.getArtistId());
			liveDisconnectListener.cancelHostEnd(request.getArtistId());
		} catch (RuntimeException e) {
			log.warn("live.host 실패: {}", e.getMessage());
			liveRealtimePublisher.sendError(me.getId(), e.getMessage());
		}
	}

	@MessageMapping("/live.join")
	public void join(LiveJoinRequest request, Authentication authentication, SimpMessageHeaderAccessor headers) {
		AuthenticatedUser me = principalOf(authentication);
		if (me == null || request == null || request.getArtistId() == null) {
			return;
		}
		try {
			User viewer = userRepository.findById(me.getId()).orElseThrow();
			LiveSession session = liveBroadcastService.requireLive(request.getArtistId());
			liveBroadcastService.requireCanWatch(viewer, request.getArtistId());
			if (session.isHost(viewer)) {
				return;
			}
			liveConnectionRegistry.registerViewer(headers.getSessionId(), request.getArtistId(), viewer.getId());
			String nickname = communityJoinService.displayNickname(viewer, request.getArtistId());
			liveRealtimePublisher.notifyHost(request.getArtistId(),
					liveRealtimePublisher.joinPayload(viewer.getId(), nickname));
		} catch (RuntimeException e) {
			log.warn("live.join 실패: {}", e.getMessage());
			liveRealtimePublisher.sendError(me.getId(), e.getMessage());
		}
	}

	@MessageMapping("/live.leave")
	public void leave(LiveJoinRequest request, Authentication authentication, SimpMessageHeaderAccessor headers) {
		AuthenticatedUser me = principalOf(authentication);
		if (me == null || request == null || request.getArtistId() == null) {
			return;
		}
		liveConnectionRegistry.removeViewerSession(headers.getSessionId());
		liveRealtimePublisher.notifyHost(request.getArtistId(),
				liveRealtimePublisher.leavePayload(me.getId()));
	}

	@MessageMapping("/live.signal")
	public void signal(LiveSignalRequest request, Authentication authentication) {
		AuthenticatedUser me = principalOf(authentication);
		if (me == null || request == null || request.getArtistId() == null || request.getToUserId() == null) {
			return;
		}
		String type = request.getType() == null ? "" : request.getType().trim().toLowerCase();
		if (!SIGNAL_TYPES.contains(type)) {
			return;
		}
		try {
			User sender = userRepository.findById(me.getId()).orElseThrow();
			LiveSession session = liveBroadcastService.requireLive(request.getArtistId());
			liveBroadcastService.requireCanWatch(sender, request.getArtistId());

			boolean senderIsHost = session.isHost(sender);
			boolean targetIsHost = session.getHost().getId().equals(request.getToUserId());
			if (senderIsHost) {
				if (targetIsHost) {
					return;
				}
			} else if (!targetIsHost) {
				liveRealtimePublisher.sendError(me.getId(), "시그널 대상이 올바르지 않습니다.");
				return;
			}

			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("type", type);
			payload.put("fromUserId", sender.getId());
			payload.put("sdp", request.getSdp());
			payload.put("candidate", request.getCandidate());
			payload.put("sdpMid", request.getSdpMid());
			payload.put("sdpMLineIndex", request.getSdpMLineIndex());
			liveRealtimePublisher.sendToPeer(request.getArtistId(), request.getToUserId(), payload);
		} catch (RuntimeException e) {
			log.warn("live.signal 실패: {}", e.getMessage());
			liveRealtimePublisher.sendError(me.getId(), e.getMessage());
		}
	}

	@MessageMapping("/live.comment")
	public void comment(LiveCommentRequest request, Authentication authentication) {
		AuthenticatedUser me = principalOf(authentication);
		if (me == null || request == null || request.getArtistId() == null) {
			return;
		}
		try {
			User author = userRepository.findById(me.getId()).orElseThrow();
			LiveCommentView saved = liveBroadcastService.addComment(author, request.getArtistId(), request.getContent());
			liveRealtimePublisher.publishComment(request.getArtistId(), saved);
		} catch (RuntimeException e) {
			log.warn("live.comment 실패: {}", e.getMessage());
			liveRealtimePublisher.sendError(me.getId(), e.getMessage());
		}
	}

	private AuthenticatedUser principalOf(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser me)) {
			return null;
		}
		return me;
	}
}
