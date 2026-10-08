package megane6.weplanet.controller.calendar;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.*;
import megane6.weplanet.domain.entity.enumfolder.LiveSessionStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.live.LiveSession;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.repository.comment.CommentRepository;
import megane6.weplanet.repository.fan.PostRepository;
import megane6.weplanet.repository.notice.SiteNoticeRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.repository.live.LiveSessionRepository;
import megane6.weplanet.repository.portal.PortalNoticeRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.calendar.ArtistAttendanceService;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.context.MessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScheduleApiController {

	/**
	 * 알림 문구는 MessageSource(messages*.properties)에서 서비스 지원 언어(ko/ja/en)별로 만든다.
	 * global-icons.js의 tr(obj)가 obj[현재 언어] || obj.ko || obj.en 으로 꺼내 쓰므로 응답은 언어별 Map 모양을 유지한다.
	 */
	private static final Map<String, Locale> NOTIFY_LOCALES = Map.of(
			"ko", Locale.KOREAN,
			"ja", Locale.JAPANESE,
			"en", Locale.ENGLISH
	);

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
	private final MessageSource messageSource;
	private final CommunityArtistResolver communityArtistResolver;

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
		// 아티스트 쪽 계정이면 "내 커뮤니티" 하나만, 팬이면 가입한 커뮤니티들
		Long ownCommunityId = communityArtistResolver.ownCommunityId(me);
		Set<Long> joined = artistId != null
				? Set.of(artistId)
				: ownCommunityId != null
				? Set.of(ownCommunityId)
				: communityJoinService.joinedArtistIds(me);
		
		Map<Long, LocalDateTime> joinedAtByArtist = ownCommunityId != null
				? Map.of()
				: communityJoinService.joinedAtByArtistId(me);
		DateTimeFormatter joinFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
		List<Map<String, String>> communities = artists.stream()
				.filter(artist -> joined.contains(artist.getId()))
				.map(artist -> {
					Map<String, String> row = new LinkedHashMap<>();
					row.put("id", String.valueOf(artist.getId()));
					row.put("name", artist.getNickname());
					LocalDateTime joinedAt = joinedAtByArtist.get(artist.getId());
					if (joinedAt != null) {
						row.put("joinedAt", joinedAt.format(joinFmt));
					}
					return row;
				})
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
		Map<Long, LocalDateTime> joinedAtByArtist = resolveJoinedAtByArtist(me, artistId);
		Set<Long> artistIds = joinedAtByArtist.keySet();

		DateTimeFormatter dateTime = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
		List<Map<String, Object>> items = new ArrayList<>();

		if (!artistIds.isEmpty()) {
			postRepository
					.findTop20ByBoardTypeAndArtist_IdInOrderByCreatedAtDesc(BoardType.ARTIST, artistIds)
					.stream()
					.filter(post -> afterJoin(post.getCreatedAt(), post.getArtist().getId(), joinedAtByArtist))
					.forEach(post -> items.add(toPostNotification(post, dateTime)));
			portalNoticeRepository
					.findTop20ByPublishedTrueAndArtist_IdInOrderByCreatedAtDesc(artistIds)
					.stream()
					.filter(notice -> afterJoin(notice.getCreatedAt(), notice.getArtist().getId(), joinedAtByArtist))
					.forEach(notice -> items.add(toCommunityNoticeNotification(notice, dateTime)));
			liveSessionRepository
					.findLiveByArtistIds(LiveSessionStatus.LIVE, artistIds)
					.stream()
					.filter(session -> afterJoin(session.getStartedAt(), session.getArtist().getId(), joinedAtByArtist))
					.limit(20)
					.forEach(session -> items.add(toLiveStartNotification(session, dateTime)));
		}

		// 시스템 공지(site_notice)는 헤더 알림이 아니라 햄버거 공지사항 뱃지로만 안내한다.

		// 내 글 댓글 알림: 가입 커뮤니티와 무관하게 본인 게시글 기준
		commentRepository.findRecentOnMyPosts(me).stream()
				.limit(20)
				.forEach(comment -> items.add(toCommentNotification(comment, dateTime)));

		items.sort(Comparator.comparing((Map<String, Object> n) -> String.valueOf(n.get("time"))).reversed());
		return Map.of("posts", items.size() > 50 ? items.subList(0, 50) : items);
	}

	/** 햄버거 메뉴 공지사항 뱃지용 — 공개된 시스템 공지 id 목록 */
	@GetMapping("/site-notices")
	public Map<String, Object> siteNotices() {
		DateTimeFormatter dateTime = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
		List<Map<String, Object>> notices = siteNoticeRepository.findVisible(null).stream()
				.map(notice -> {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("id", notice.getId());
					row.put("title", notice.getTitle());
					row.put("createdAt", notice.getCreatedAt() != null
							? notice.getCreatedAt().format(dateTime)
							: null);
					return row;
				})
				.toList();
		return Map.of("notices", notices);
	}

	private Map<Long, LocalDateTime> resolveJoinedAtByArtist(User me, Long artistId) {
		Long ownCommunityId = communityArtistResolver.ownCommunityId(me);
		if (ownCommunityId != null) {
			return Map.of(ownCommunityId, LocalDateTime.MIN);
		}
		Map<Long, LocalDateTime> all = communityJoinService.joinedAtByArtistId(me);
		if (artistId == null) {
			return all;
		}
		LocalDateTime joinedAt = all.get(artistId);
		return joinedAt == null ? Map.of() : Map.of(artistId, joinedAt);
	}

	private boolean afterJoin(LocalDateTime eventTime, Long artistId, Map<Long, LocalDateTime> joinedAtByArtist) {
		if (eventTime == null || artistId == null) {
			return false;
		}
		LocalDateTime joinedAt = joinedAtByArtist.get(artistId);
		if (joinedAt == null) {
			return false;
		}
		if (LocalDateTime.MIN.equals(joinedAt)) {
			return true;
		}
		return !eventTime.isBefore(joinedAt);
	}

	/** 메시지 키 하나를 서비스 지원 언어(ko/ja/en)별 문구 Map으로 만든다. */
	private Map<String, String> localized(String code, Object... args) {
		Map<String, String> byLang = new LinkedHashMap<>();
		NOTIFY_LOCALES.forEach((lang, locale) -> byLang.put(lang,
				messageSource.getMessage(code, (args == null || args.length == 0) ? null : args, locale)));
		return byLang;
	}

	/** 게시글 제목처럼 번역하지 않는 사용자 입력값을 언어별 Map 모양으로 감싼다. */
	private Map<String, String> same(String value) {
		Map<String, String> byLang = new LinkedHashMap<>();
		NOTIFY_LOCALES.keySet().forEach(lang -> byLang.put(lang, value == null ? "" : value));
		return byLang;
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
		notification.put("category", localized("notify.category.artistPost"));
		notification.put("title", same(post.getTitle()));
		notification.put("message", localized("notify.message.newPost", post.getArtist().getNickname(), post.getTitle()));
		return notification;
	}

	private Map<String, Object> toCommentNotification(Comment comment, DateTimeFormatter dateTime) {
		Post post = comment.getPost();
		User commenter = comment.getAuthor();
		boolean artistComment = commenter.isArtistSide();
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
		notification.put("category", localized(artistComment
				? "notify.category.artistComment"
				: "notify.category.myPostComment"));
		String preview = comment.getContent() == null ? ""
				: (comment.getContent().length() > 40
				? comment.getContent().substring(0, 40) + "…"
				: comment.getContent());
		notification.put("title", same(preview));
		if (commenter.getNickname() != null) {
			notification.put("message", localized("notify.message.commented", commenter.getNickname(), preview));
		} else {
			// 닉네임이 없는 경우 "누군가"도 언어별로 번역해서 넣는다
			Map<String, String> message = new LinkedHashMap<>();
			NOTIFY_LOCALES.forEach((lang, locale) -> message.put(lang, messageSource.getMessage(
					"notify.message.commented",
					new Object[]{messageSource.getMessage("notify.someone", null, locale), preview},
					locale)));
			notification.put("message", message);
		}
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
		notification.put("category", localized("notify.category.liveStart"));
		notification.put("title", localized("notify.title.live", artist.getNickname()));
		notification.put("message", localized("notify.message.liveStarted", artist.getNickname()));
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
		notification.put("category", localized("notify.category.communityNotice"));
		notification.put("title", same(notice.getTitle()));
		notification.put("message", localized("notify.message.communityNotice", artist.getNickname(), notice.getTitle()));
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
		notification.put("category", localized("notify.category.siteNotice"));
		notification.put("title", same(notice.getTitle()));
		notification.put("message", localized("notify.message.siteNotice", notice.getTitle()));
		return notification;
	}

	@GetMapping("/artists")
	public List<ArtistCardView> artists() {
		return userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();
	}
}
