package megane6.weplanet.controller.live;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.AuthenticatedUserResolver;
import megane6.weplanet.domain.dto.live.LiveCommentView;
import megane6.weplanet.domain.dto.live.LiveStatusView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.live.LiveBroadcastService;
import megane6.weplanet.service.live.LiveRealtimePublisher;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LiveApiController {

	private static final String SESSION_ARTIST = "portalArtistId";

	private final LiveBroadcastService liveBroadcastService;
	private final LiveRealtimePublisher liveRealtimePublisher;
	private final UserRepository userRepository;
	private final AuthenticatedUserResolver userResolver;

	@PostMapping("/api/portal/live/start")
	public LiveStatusView start(@AuthenticationPrincipal AuthenticatedUser principal, HttpSession session) {
		User host = requirePortalUser(principal);
		User artist = resolveManagedArtist(host, session);
		LiveStatusView status = liveBroadcastService.start(host, artist);
		liveRealtimePublisher.publishStatus(status, artist.getId());
		return status;
	}

	@PostMapping("/api/portal/live/end")
	public LiveStatusView end(@AuthenticationPrincipal AuthenticatedUser principal, HttpSession session) {
		User host = requirePortalUser(principal);
		User artist = resolveManagedArtist(host, session);
		LiveStatusView status = liveBroadcastService.end(host, artist);
		liveRealtimePublisher.publishStatus(status, artist.getId());
		return status;
	}

	@PostMapping("/api/portal/live/replay")
	public Map<String, Object> saveReplay(@AuthenticationPrincipal AuthenticatedUser principal,
										  HttpSession session,
										  @RequestParam("file") MultipartFile file) {
		User host = requirePortalUser(principal);
		User artist = resolveManagedArtist(host, session);
		Long mediaId = liveBroadcastService.saveReplay(host, artist, file);
		return Map.of(
				"success", true,
				"mediaId", mediaId
		);
	}

	@GetMapping("/api/portal/live/status")
	public LiveStatusView portalStatus(@AuthenticationPrincipal AuthenticatedUser principal, HttpSession session) {
		User host = requirePortalUser(principal);
		User artist = resolveManagedArtist(host, session);
		return liveBroadcastService.status(artist.getId());
	}

	@GetMapping("/api/community/{artistId}/live/status")
	public LiveStatusView communityStatus(@PathVariable Long artistId,
										  @AuthenticationPrincipal AuthenticatedUser principal) {
		User me = userResolver.requireAuthenticated(principal);
		liveBroadcastService.requireCanWatch(me, artistId);
		return liveBroadcastService.status(artistId);
	}

	@GetMapping("/api/community/{artistId}/live/comments")
	public Map<String, List<LiveCommentView>> communityComments(
			@PathVariable Long artistId,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		User me = userResolver.requireAuthenticated(principal);
		return Map.of("comments", liveBroadcastService.comments(me, artistId));
	}

	private User requirePortalUser(AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return userRepository.findById(principal.getId())
				.filter(user -> user.getRole() == Role.ARTIST || user.getRole() == Role.AGENCY)
				.orElseThrow(() -> new IllegalStateException("아티스트 또는 에이전시만 이용할 수 있습니다."));
	}

	private User resolveManagedArtist(User actor, HttpSession session) {
		if (actor.getRole() == Role.ARTIST) {
			return actor;
		}
		List<User> artists = userRepository.findByRole(Role.ARTIST);
		if (artists.isEmpty()) {
			throw new IllegalStateException("관리할 아티스트가 없습니다.");
		}
		Long selected = null;
		if (session != null) {
			Object stored = session.getAttribute(SESSION_ARTIST);
			if (stored instanceof Long storedId) {
				selected = storedId;
			} else if (stored instanceof Number number) {
				selected = number.longValue();
			}
		}
		final Long selectedId = selected;
		return artists.stream()
				.filter(item -> selectedId != null && item.getId().equals(selectedId))
				.findFirst()
				.orElse(artists.get(0));
	}
}
