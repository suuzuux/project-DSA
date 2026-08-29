package megane6.weplanet.controller.calendar;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.AuthenticatedUserResolver;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.PostRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScheduleApiController {

	private final PortalManagementService portalManagementService;
	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final AuthenticatedUserResolver userResolver;
	private final CommunityJoinService communityJoinService;

	@GetMapping("/schedules")
	public Map<String, Object> schedules(@AuthenticationPrincipal AuthenticatedUser principal,
	                                     @RequestParam(required = false) Long artistId) {
		List<User> artists = userRepository.findByRole(Role.ARTIST);
		Map<String, Object> body = new LinkedHashMap<>();

		if (principal == null) {
			body.put("communities", artists.stream()
					.map(artist -> Map.of(
							"id", String.valueOf(artist.getId()),
							"name", artist.getNickname()
					))
					.toList());
			body.put("eventsByDate", portalManagementService.getPublicEventsByDate());
			return body;
		}

		User me = userResolver.requireAuthenticated(principal);
		Set<Long> joined = artistId != null
				? Set.of(artistId)
				: me.getRole() == Role.ARTIST
				? Set.of(me.getId())
				: communityJoinService.joinedProfilesByArtistId(me).keySet();

		List<Map<String, String>> communities = artists.stream()
				.filter(artist -> joined.contains(artist.getId()))
				.map(artist -> Map.of(
						"id", String.valueOf(artist.getId()),
						"name", artist.getNickname()
				))
				.toList();

		body.put("communities", communities);
		body.put("eventsByDate", joined.isEmpty()
				? Map.of()
				: portalManagementService.getPublicEventsByDateForArtists(joined));
		return body;
	}

	@GetMapping("/notifications")
	public Map<String, Object> notifications(@AuthenticationPrincipal AuthenticatedUser principal,
	                                         @RequestParam(required = false) Long artistId) {
		if (principal == null) {
			return Map.of("posts", List.of());
		}

		User me = userResolver.requireAuthenticated(principal);
		Set<Long> artistIds = artistId != null
				? Set.of(artistId)
				: me.getRole() == Role.ARTIST
				? Set.of(me.getId())
				: communityJoinService.joinedProfilesByArtistId(me).keySet();
		if (artistIds.isEmpty()) {
			return Map.of("posts", List.of());
		}

		DateTimeFormatter dateTime = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
		List<Map<String, Object>> posts = postRepository
				.findTop20ByBoardTypeAndArtist_IdInOrderByCreatedAtDesc(BoardType.ARTIST, artistIds)
				.stream()
				.map(post -> toPostNotification(post, dateTime))
				.toList();
		return Map.of("posts", posts);
	}

	private Map<String, Object> toPostNotification(Post post, DateTimeFormatter dateTime) {
		Map<String, Object> notification = new LinkedHashMap<>();
		notification.put("id", "post-" + post.getId());
		notification.put("type", "post");
		notification.put("eventId", null);
		notification.put("date", post.getCreatedAt().toLocalDate().toString());
		notification.put("time", post.getCreatedAt().format(dateTime));
		notification.put("read", false);
		notification.put("artistId", String.valueOf(post.getArtist().getId()));
		notification.put("artist", post.getArtist().getNickname());
		notification.put("artistName", post.getArtist().getNickname());
		notification.put("artistLogo", ArtistCardView.from(post.getArtist()).logo());
		notification.put("postUrl", "/community/" + post.getArtist().getId() + "/artist/" + post.getId());
		notification.put("category", Map.of(
				"ko", "아티스트 게시글",
				"en", "Artist post",
				"ja", "アーティスト投稿",
				"zh", "艺人帖子",
				"fr", "Post de l'artiste",
				"es", "Publicación del artista"
		));
		notification.put("title", Map.of(
				"ko", post.getTitle(),
				"en", post.getTitle(),
				"ja", post.getTitle(),
				"zh", post.getTitle(),
				"fr", post.getTitle(),
				"es", post.getTitle()
		));
		notification.put("message", Map.of(
				"ko", post.getArtist().getNickname() + "의 새 게시글: " + post.getTitle(),
				"en", "New post from " + post.getArtist().getNickname() + ": " + post.getTitle(),
				"ja", post.getArtist().getNickname() + "の新しい投稿: " + post.getTitle(),
				"zh", post.getArtist().getNickname() + "的新帖子：" + post.getTitle(),
				"fr", "Nouveau post de " + post.getArtist().getNickname() + " : " + post.getTitle(),
				"es", "Nueva publicación de " + post.getArtist().getNickname() + ": " + post.getTitle()
		));
		return notification;
	}

	@GetMapping("/artists")
	public List<ArtistCardView> artists() {
		return userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();
	}
}
