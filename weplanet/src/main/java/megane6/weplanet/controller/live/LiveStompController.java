package megane6.weplanet.controller.live;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.live.LiveCommentRequest;
import megane6.weplanet.domain.dto.live.LiveCommentView;
import megane6.weplanet.domain.dto.live.LiveJoinRequest;
import megane6.weplanet.domain.dto.live.LiveSignalRequest;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.live.LiveSession;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.i18n.PreferredLocaleResolver;
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
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
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
	private final Messages messages;

	// STOMP 처리 스레드에는 요청 로케일(LocaleContextHolder)이 없으므로, 오류를 받을 사람의
	// preferredLanguage로 로케일을 정해 메시지 키(또는 아직 키가 아닌 문장)를 번역해서 보낸다.
	private void sendError(Long userId, String codeOrText) {
		liveRealtimePublisher.sendError(userId, messages.resolve(codeOrText, localeOf(userId)));
	}

	// catch 블록용: 예외를 통째로 번역한다(값을 들고 다니는 LocalizedMessage 예외도 {0}이 빠지지 않게).
	// 메시지가 없거나 orElseThrow() 의 "No value present" 같은 내부 문구는 공통 오류 문구로 바꿔 보낸다.
	private void sendError(Long userId, RuntimeException e) {
		Locale locale = localeOf(userId);
		String text = e instanceof NoSuchElementException ? null : messages.resolve(e, locale);
		if (text == null || text.isBlank()) {
			text = messages.resolve("error.unexpected", locale);
		}
		liveRealtimePublisher.sendError(userId, text);
	}

	private Locale localeOf(Long userId) {
		return userRepository.findById(userId)
				.map(user -> PreferredLocaleResolver.toLocale(user.getPreferredLanguage()))
				.orElse(Locale.KOREAN);
	}

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
				sendError(me.getId(), "error.live.notHost");
				return;
			}
			liveConnectionRegistry.registerHost(headers.getSessionId(), request.getArtistId());
			liveDisconnectListener.cancelHostEnd(request.getArtistId());
		} catch (RuntimeException e) {
			log.warn("live.host 실패: {}", e.getMessage());
			sendError(me.getId(), e);
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
			sendError(me.getId(), e);
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
				sendError(me.getId(), "error.live.invalidSignalTarget");
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
			sendError(me.getId(), e);
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
			sendError(me.getId(), e);
		}
	}

	private AuthenticatedUser principalOf(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser me)) {
			return null;
		}
		return me;
	}
}
