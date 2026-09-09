package megane6.weplanet.controller.calendar;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.AuthenticatedUserResolver;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.SiteNotice;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.LiveSessionStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.live.LiveSession;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.repository.PostRepository;
import megane6.weplanet.repository.SiteNoticeRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.live.LiveSessionRepository;
import megane6.weplanet.repository.portal.PortalNoticeRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.calendar.ArtistAttendanceService;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScheduleApiController {

	private final PortalManagementService portalManagementService;
	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final PortalNoticeRepository portalNoticeRepository;
	private final SiteNoticeRepository siteNoticeRepository;
	private final CommentRepository commentRepository;
	private final LiveSessionRepository liveSessionRepository;
	private final AuthenticatedUserResolver userResolver;
	private final CommunityJoinService communityJoinService;
	private final ArtistAttendanceService artistAttendanceService;

	@GetMapping("/schedules")
	public Map<String, Object> schedules(@AuthenticationPrincipal AuthenticatedUser principal,
	                                     @RequestParam(required = false) Long artistId) {
		Map<String, Object> body = new LinkedHashMap<>();

		if (principal == null) {
			body.put("communities", List.of());
			body.put("eventsByDate", Map.of());
			body.put("attendance", Map.of());
			return body;
		}

		List<User> artists = userRepository.findByRole(Role.ARTIST);
		User me = userResolver.requireAuthenticated(principal);
		Set<Long> joined = artistId != null
				? Set.of(artistId)
				: me.getRole() == Role.ARTIST
				? Set.of(me.getId())
				: communityJoinService.joinedArtistIds(me);

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

		// 출석은 "요청한 커뮤니티 주인"만. artistId 없으면 로그인 아티스트 출석으로 절대 fallback 하지 않음
		// (타 커뮤니티 캘린더에 본인 도장이 새는 버그 방지)
		Map<String, String> attendance = Map.of();
		if (artistId != null) {
			User attendanceArtist = userRepository.findById(artistId)
					.filter(user -> user.getRole() == Role.ARTIST)
					.orElse(null);
			attendance = artistAttendanceService.getAllPawColors(attendanceArtist);
		}
		body.put("attendance", attendance);
		body.put("attendanceArtistId", artistId);
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
				: communityJoinService.joinedArtistIds(me);

		DateTimeFormatter dateTime = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
		List<Map<String, Object>> items = new ArrayList<>();

		if (!artistIds.isEmpty()) {
			postRepository
					.findTop20ByBoardTypeAndArtist_IdInOrderByCreatedAtDesc(BoardType.ARTIST, artistIds)
					.forEach(post -> items.add(toPostNotification(post, dateTime)));
			portalNoticeRepository
					.findTop20ByPublishedTrueAndArtist_IdInOrderByCreatedAtDesc(artistIds)
					.forEach(notice -> items.add(toCommunityNoticeNotification(notice, dateTime)));
			liveSessionRepository
					.findLiveByArtistIds(LiveSessionStatus.LIVE, artistIds)
					.stream()
					.limit(20)
					.forEach(session -> items.add(toLiveStartNotification(session, dateTime)));
		}

		siteNoticeRepository.findTop20ByPublishedTrueOrderByCreatedAtDesc()
				.forEach(notice -> items.add(toSiteNoticeNotification(notice, dateTime)));

		// 내 글 댓글 알림: 가입 커뮤니티와 무관하게 본인 게시글 기준
		commentRepository.findRecentOnMyPosts(me).stream()
				.limit(20)
				.forEach(comment -> items.add(toCommentNotification(comment, dateTime)));

		items.sort(Comparator.comparing((Map<String, Object> n) -> String.valueOf(n.get("time"))).reversed());
		return Map.of("posts", items.size() > 50 ? items.subList(0, 50) : items);
	}

	private Map<String, Object> toPostNotification(Post post, DateTimeFormatter dateTime) {
		Map<String, Object> notification = new LinkedHashMap<>();
		notification.put("id", "post-" + post.getId());
		notification.put("type", "post");
		notification.put("eventId", null);
		notification.put("date", post.getCreatedAt().toLocalDate().toString());
		notification.put("time", post.getCreatedAt().format(dateTime));
		notification.put("read", false);
		notification.put("global", false);
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

	private Map<String, Object> toCommentNotification(Comment comment, DateTimeFormatter dateTime) {
		Post post = comment.getPost();
		User commenter = comment.getAuthor();
		boolean artistComment = commenter.getRole() == Role.ARTIST;
		String type = artistComment ? "artist_comment" : "comment";
		String idPrefix = artistComment ? "artist-comment-" : "comment-";

		Long communityId = post.getArtist() != null ? post.getArtist().getId() : null;
		String tab = post.getBoardType() == BoardType.ARTIST ? "artist" : "fan";
		String postUrl = communityId != null
				? "/community/" + communityId + "/" + tab + "/" + post.getId()
				: "/posts/detail/" + post.getId();

		String artistName = post.getArtist() != null ? post.getArtist().getNickname() : "WePlaNet";
		String artistLogo = post.getArtist() != null ? ArtistCardView.from(post.getArtist()).logo() : "WP";

		Map<String, Object> notification = new LinkedHashMap<>();
		notification.put("id", idPrefix + comment.getId());
		notification.put("type", type);
		notification.put("eventId", null);
		notification.put("date", comment.getCreatedAt().toLocalDate().toString());
		notification.put("time", comment.getCreatedAt().format(dateTime));
		notification.put("read", false);
		notification.put("global", true); // 내 글 댓글은 커뮤니티 필터와 무관하게 항상 표시
		notification.put("artistId", communityId != null ? String.valueOf(communityId) : "system");
		notification.put("artist", artistName);
		notification.put("artistName", artistName);
		notification.put("artistLogo", artistLogo);
		notification.put("postUrl", postUrl);
		notification.put("category", artistComment
				? Map.of(
						"ko", "아티스트 댓글",
						"en", "Artist comment",
						"ja", "アーティストコメント",
						"zh", "艺人评论",
						"fr", "Commentaire artiste",
						"es", "Comentario del artista")
				: Map.of(
						"ko", "내 글 댓글",
						"en", "Comment on your post",
						"ja", "あなたの投稿へのコメント",
						"zh", "我的帖子评论",
						"fr", "Commentaire sur votre post",
						"es", "Comentario en tu publicación"));
		String preview = comment.getContent() == null ? ""
				: (comment.getContent().length() > 40
				? comment.getContent().substring(0, 40) + "…"
				: comment.getContent());
		notification.put("title", Map.of(
				"ko", preview,
				"en", preview,
				"ja", preview,
				"zh", preview,
				"fr", preview,
				"es", preview
		));
		String who = commenter.getNickname() != null ? commenter.getNickname() : "Someone";
		notification.put("message", artistComment
				? Map.of(
						"ko", who + "님이 내 글에 댓글을 남겼습니다: " + preview,
						"en", who + " commented on your post: " + preview,
						"ja", who + "さんがあなたの投稿にコメントしました: " + preview,
						"zh", who + "评论了你的帖子：" + preview,
						"fr", who + " a commenté votre post : " + preview,
						"es", who + " comentó tu publicación: " + preview)
				: Map.of(
						"ko", who + "님이 내 글에 댓글을 남겼습니다: " + preview,
						"en", who + " commented on your post: " + preview,
						"ja", who + "さんがあなたの投稿にコメントしました: " + preview,
						"zh", who + "评论了你的帖子：" + preview,
						"fr", who + " a commenté votre post : " + preview,
						"es", who + " comentó tu publicación: " + preview));
		return notification;
	}

	private Map<String, Object> toLiveStartNotification(LiveSession session, DateTimeFormatter dateTime) {
		User artist = session.getArtist();
		Map<String, Object> notification = new LinkedHashMap<>();
		notification.put("id", "live-" + session.getId());
		notification.put("type", "live_start");
		notification.put("eventId", null);
		notification.put("date", session.getStartedAt().toLocalDate().toString());
		notification.put("time", session.getStartedAt().format(dateTime));
		notification.put("read", false);
		notification.put("global", false);
		notification.put("artistId", String.valueOf(artist.getId()));
		notification.put("artist", artist.getNickname());
		notification.put("artistName", artist.getNickname());
		notification.put("artistLogo", ArtistCardView.from(artist).logo());
		notification.put("postUrl", "/community/" + artist.getId() + "/live");
		notification.put("category", Map.of(
				"ko", "라이브 시작",
				"en", "Live started",
				"ja", "ライブ開始",
				"zh", "直播开始",
				"fr", "Live commencé",
				"es", "Live iniciado"
		));
		notification.put("title", Map.of(
				"ko", artist.getNickname() + " 라이브 방송 중",
				"en", artist.getNickname() + " is live",
				"ja", artist.getNickname() + "がライブ中",
				"zh", artist.getNickname() + "正在直播",
				"fr", artist.getNickname() + " est en live",
				"es", artist.getNickname() + " está en vivo"
		));
		notification.put("message", Map.of(
				"ko", artist.getNickname() + "님의 라이브 방송이 시작되었습니다.",
				"en", artist.getNickname() + "'s live broadcast has started.",
				"ja", artist.getNickname() + "のライブが始まりました。",
				"zh", artist.getNickname() + "的直播已开始。",
				"fr", "Le live de " + artist.getNickname() + " a commencé.",
				"es", "El live de " + artist.getNickname() + " ha comenzado."
		));
		return notification;
	}

	private Map<String, Object> toCommunityNoticeNotification(PortalNotice notice, DateTimeFormatter dateTime) {
		User artist = notice.getArtist();
		Map<String, Object> notification = new LinkedHashMap<>();
		notification.put("id", "community-notice-" + notice.getId());
		notification.put("type", "community_notice");
		notification.put("eventId", null);
		notification.put("date", notice.getCreatedAt().toLocalDate().toString());
		notification.put("time", notice.getCreatedAt().format(dateTime));
		notification.put("read", false);
		notification.put("global", false);
		notification.put("artistId", String.valueOf(artist.getId()));
		notification.put("artist", artist.getNickname());
		notification.put("artistName", artist.getNickname());
		notification.put("artistLogo", ArtistCardView.from(artist).logo());
		notification.put("postUrl", "/community/" + artist.getId() + "/notice/" + notice.getId());
		notification.put("category", Map.of(
				"ko", "커뮤니티 공지",
				"en", "Community notice",
				"ja", "コミュニティお知らせ",
				"zh", "社区公告",
				"fr", "Avis communauté",
				"es", "Aviso de comunidad"
		));
		notification.put("title", Map.of(
				"ko", notice.getTitle(),
				"en", notice.getTitle(),
				"ja", notice.getTitle(),
				"zh", notice.getTitle(),
				"fr", notice.getTitle(),
				"es", notice.getTitle()
		));
		notification.put("message", Map.of(
				"ko", artist.getNickname() + " 커뮤니티 공지: " + notice.getTitle(),
				"en", "Community notice from " + artist.getNickname() + ": " + notice.getTitle(),
				"ja", artist.getNickname() + "コミュニティお知らせ: " + notice.getTitle(),
				"zh", artist.getNickname() + "社区公告：" + notice.getTitle(),
				"fr", "Avis de " + artist.getNickname() + " : " + notice.getTitle(),
				"es", "Aviso de " + artist.getNickname() + ": " + notice.getTitle()
		));
		return notification;
	}

	private Map<String, Object> toSiteNoticeNotification(SiteNotice notice, DateTimeFormatter dateTime) {
		Map<String, Object> notification = new LinkedHashMap<>();
		notification.put("id", "site-notice-" + notice.getId());
		notification.put("type", "site_notice");
		notification.put("eventId", null);
		notification.put("date", notice.getCreatedAt().toLocalDate().toString());
		notification.put("time", notice.getCreatedAt().format(dateTime));
		notification.put("read", false);
		notification.put("global", true);
		notification.put("artistId", "system");
		notification.put("artist", "WePlaNet");
		notification.put("artistName", "WePlaNet");
		notification.put("artistLogo", "WP");
		notification.put("postUrl", "/notices/" + notice.getId());
		notification.put("category", Map.of(
				"ko", "시스템 공지",
				"en", "System notice",
				"ja", "システムお知らせ",
				"zh", "系统公告",
				"fr", "Avis système",
				"es", "Aviso del sistema"
		));
		notification.put("title", Map.of(
				"ko", notice.getTitle(),
				"en", notice.getTitle(),
				"ja", notice.getTitle(),
				"zh", notice.getTitle(),
				"fr", notice.getTitle(),
				"es", notice.getTitle()
		));
		notification.put("message", Map.of(
				"ko", "시스템 공지: " + notice.getTitle(),
				"en", "System notice: " + notice.getTitle(),
				"ja", "システムお知らせ: " + notice.getTitle(),
				"zh", "系统公告：" + notice.getTitle(),
				"fr", "Avis système : " + notice.getTitle(),
				"es", "Aviso del sistema: " + notice.getTitle()
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
