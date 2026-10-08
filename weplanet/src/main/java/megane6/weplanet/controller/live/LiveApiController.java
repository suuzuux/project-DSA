package megane6.weplanet.controller.live;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;
import megane6.weplanet.domain.dto.live.LiveCommentView;
import megane6.weplanet.domain.dto.live.LiveStatusView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.live.LiveComment;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.fan.ReportService;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.live.LiveBroadcastService;
import megane6.weplanet.service.live.LiveRealtimePublisher;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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
	private final ReportService reportService;
	private final CommunityArtistResolver communityArtistResolver;

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

	@PostMapping("/api/community/{artistId}/live/comments/{commentId}/report")
	public Map<String, Object> reportLiveComment(
			@PathVariable Long artistId,
			@PathVariable Long commentId,
			@RequestParam String reason,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		User me = userResolver.requireAuthenticated(principal);
		liveBroadcastService.requireCanWatch(me, artistId);
		LiveComment comment = liveBroadcastService.requireCommentForArtist(commentId, artistId);
		if (comment.getAuthor().getId().equals(me.getId())) {
			throw new IllegalStateException("error.live.cannotReportOwn");
		}
		ReportReason reportReason;
		try {
			reportReason = ReportReason.valueOf(reason);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new IllegalArgumentException("error.live.invalidReportReason");
		}
		reportService.reportLiveComment(comment, me, reportReason);
		return Map.of("success", true);
	}
	
	private User requirePortalUser(AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return userRepository.findById(principal.getId())
				.filter(user -> user.isArtistSide() || user.getRole() == Role.AGENCY)
				.orElseThrow(() -> new IllegalStateException("error.live.artistOrAgencyOnlyUse"));
	}
	
	private User resolveManagedArtist(User actor, HttpSession session) {
		// 아티스트 쪽 계정은 "내 커뮤니티"(솔로=본인, 멤버=소속 그룹)가 곧 방송 대상
		if (actor.isArtistSide()) {
			Long ownCommunityId = communityArtistResolver.ownCommunityId(actor);
			if (ownCommunityId == null) {
				throw new IllegalStateException("error.live.noOwnCommunity");
			}
			return userRepository.findById(ownCommunityId)
					.orElseThrow(() -> new IllegalStateException("error.live.communityNotFound"));
		}
		Long agencyId = actor.agencyId();
		List<User> artists = agencyId == null
				? List.of()
				: userRepository.findByRoleAndAgency_Id(Role.ARTIST, agencyId);
		if (artists.isEmpty()) {
			throw new IllegalStateException("error.live.noManagedArtist");
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
