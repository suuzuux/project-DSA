package megane6.weplanet.controller.common;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** JS 화면에서 쓰는 다국어 문구 API (현재 화면 언어 기준). */
@RestController
@RequiredArgsConstructor
public class I18nController {

	private final MessageSource messageSource;

	private static final String[] SHELL_KEYS = {
			"shell.menu.title",
			"shell.menu.close",
			"shell.menu.open",
			"shell.menu.greet",
			"shell.community.title",
			"shell.community.explore",
			"shell.community.empty",
			"shell.community.emptyJoined",
			"shell.community.myCommunity",
			"shell.community.joinedCommunity",
			"shell.nav.collection",
			"shell.nav.notice",
			"shell.nav.noticeUnread",
			"shell.nav.shop",
			"shell.nav.settings",
			"shell.nav.keyword",
			"shell.guest.greet",
			"shell.guest.signup",
			"shell.fab.calendar",
			"shell.fab.chatFan",
			"shell.fab.chatDm",
			"shell.fab.chatOpen",
			"shell.admin.pageLink",
			// 드로어 커뮤니티 검색·가입 모달 문구
			"layout.header.searchTitle",
			"main.search.placeholder",
			"main.search.gender",
			"main.search.all",
			"main.search.genderMale",
			"main.search.genderFemale",
			"main.search.genderMixed",
			"main.search.category",
			"main.search.categoryIdol",
			"main.search.categoryActor",
			"main.search.submit",
			"community.join.button",
			"community.join.thisCommunity",
			"community.join.modalPrompt",
			"community.join.nicknameLabel",
			"community.join.nicknamePlaceholder",
			"community.join.help",
			"community.join.submit",
			// DM 패널 / 멤버십 가입·상세 모달
			"shell.artistFallback",
			"shell.dm.panelLabel",
			"shell.dm.addFriend",
			"shell.dm.friend",
			"shell.dm.close",
			"shell.dm.promoTitle",
			"shell.dm.promoDesc",
			"shell.dm.messages",
			"shell.dm.noMessages",
			"shell.dm.recommend",
			"shell.dm.noRecommend",
			"shell.dm.backToList",
			"shell.dm.search",
			"shell.dm.more",
			"shell.dm.expiredTitle",
			"shell.dm.expiredDesc",
			"shell.dm.joinTitle",
			"shell.dm.joinDesc",
			"shell.dm.startConversation",
			"shell.dm.attach",
			"shell.dm.inputPlaceholder",
			"shell.dm.send",
			"shell.dm.quota",
			"shell.dm.fanDm",
			"shell.dm.justNow",
			"shell.membership.heroTitle",
			"shell.membership.benefit1",
			"shell.membership.benefit2",
			"shell.membership.benefit3",
			"shell.membership.benefit4",
			"shell.membership.benefit5",
			"shell.membership.price",
			"shell.membership.vatIncluded",
			"shell.membership.join",
			"shell.membership.detailTitle",
			"shell.membership.name",
			"shell.membership.number",
			"shell.membership.period",
			"shell.membership.email",
			"shell.membership.phone",
			"shell.membership.cancelConfirm",
			"shell.membership.cancel",
	};

	/** main.js 와 커뮤니티 화면 JS 가 WePlaNet.t(key) 로 쓰는 문구 (JS 전용은 client.*). */
	private static final String[] CLIENT_KEYS = {
			// main.js - 공용 다이얼로그 / 배너 / 회원가입 폼 검증
			"common.cancel",
			"common.confirm",
			"client.carousel.slide",
			"signup.validation.usernamePattern",
			"signup.validation.passwordPattern",
			"signup.error.passwordMismatch",
			"signup.validation.realNameRequired",
			"signup.validation.emailFormat",
			"client.signup.nicknameLength",
			"client.signup.agreeRequired",
			"client.mockSubmit",
			// community-join.js
			"error.community.nicknameRequired",
			"error.community.nicknameTooLong",
			"client.join.failed",
			// community-explore.js
			"client.explore.solo",
			"client.explore.joined",
			"client.explore.join",
			"client.explore.mine",
			"main.search.categoryIdol",
			"main.search.categoryActor",
			"client.explore.searching",
			"client.explore.empty",
			"client.explore.error",
			// community-posts.js
			"community.common.loading",
			"community.common.more",
			"community.profile.listLoadFailed",
			"client.posts.moreFailed",
			"client.posts.badResponse",
			"client.posts.editorPlaceholder",
			"client.editor.markdown",
			"client.editor.markdownTitle",
			"client.editor.wysiwyg",
			"client.editor.wysiwygTitle",
			"error.post.tooManyAttachments",
			"client.posts.filesSelected",
			"client.posts.submitFailed",
			"client.posts.submitFailedCheckList",
			// community-post-detail.js
			"community.comment.reported",
			"community.translate.show",
			"client.translate.loading",
			"client.translate.original",
			"client.report.submitted",
			"client.report.failed",
			"client.summary.loading",
			"client.summary.title",
			"client.summary.failed",
			"client.request.failed",
			// community-live.js
			"client.live.anonymous",
			"community.report.toggle",
			"client.live.reportPrompt",
			"client.live.reportReasonInvalid",
			"client.live.reportFailed",
			"community.media.replayTag",
			// community-board-select.js - select에 aria-label이 없을 때의 기본 문구
			"client.board.sort",
			// dm-realtime.js
			"shell.dm.messages",
			"shell.dm.recommend",
			"shell.dm.fanDm",
			"client.dm.noConversation",
			"client.dm.loginRequired",
			"client.dm.warningPrefix",
			"client.dm.unreadLabel",
			// collection.js
			"client.collection.badgeLoadFailed",
	};

	@GetMapping(value = "/api/i18n/client", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public Map<String, String> clientMessages() {
		Map<String, String> messages = new LinkedHashMap<>();
		for (String key : CLIENT_KEYS) {
			// {0} 자리표시자는 JS 가 치환하므로 args 를 null 로 넘긴다.
			messages.put(key, messageSource.getMessage(key, null, LocaleContextHolder.getLocale()));
		}
		return messages;
	}

	@GetMapping(value = "/api/i18n/shell", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public Map<String, String> shellMessages() {
		Map<String, String> messages = new LinkedHashMap<>();
		for (String key : SHELL_KEYS) {
			// {0} 자리표시자를 그대로 남겨 shell.js 가 치환하도록 args 를 null 로 넘긴다.
			messages.put(key, messageSource.getMessage(key, null, LocaleContextHolder.getLocale()));
		}
		return messages;
	}

	/** 캘린더 위젯 문구 - 새로고침 없이 언어를 바꾸도록 ko/ja/en 을 한 번에 내려준다. */
	private static final Map<String, String> CALENDAR_UI_KEY_MAP = new LinkedHashMap<>();
	static {
		CALENDAR_UI_KEY_MAP.put("notificationsTitle", "calendar.ui.notificationsTitle");
		CALENDAR_UI_KEY_MAP.put("noNotifications", "calendar.ui.noNotifications");
		CALENDAR_UI_KEY_MAP.put("calendarTitle", "calendar.ui.calendarTitle");
		CALENDAR_UI_KEY_MAP.put("all", "calendar.ui.all");
		CALENDAR_UI_KEY_MAP.put("noEventsForDate", "calendar.ui.noEventsForDate");
		CALENDAR_UI_KEY_MAP.put("ticketCta", "calendar.ui.ticketCta");
		CALENDAR_UI_KEY_MAP.put("ticketImagePlaceholder", "calendar.ui.ticketImagePlaceholder");
		CALENDAR_UI_KEY_MAP.put("back", "calendar.ui.back");
		CALENDAR_UI_KEY_MAP.put("langChanged", "calendar.ui.langChanged");
		CALENDAR_UI_KEY_MAP.put("shopMoveTo", "calendar.ui.shopMoveTo");
		CALENDAR_UI_KEY_MAP.put("myCommunities", "calendar.ui.myCommunities");
		CALENDAR_UI_KEY_MAP.put("noJoinedCommunities", "calendar.ui.noJoinedCommunities");
		CALENDAR_UI_KEY_MAP.put("login", "calendar.ui.login");
		// 헤더 테마 전환 버튼 aria-label (메인 헤더와 같은 키 재사용)
		CALENDAR_UI_KEY_MAP.put("themeToggle", "main.header.themeToggle");
		CALENDAR_UI_KEY_MAP.put("markAllRead", "calendar.ui.markAllRead");
		CALENDAR_UI_KEY_MAP.put("weekHint", "calendar.ui.weekHint");
		CALENDAR_UI_KEY_MAP.put("prevMonth", "calendar.ui.prevMonth");
		CALENDAR_UI_KEY_MAP.put("nextMonth", "calendar.ui.nextMonth");
		CALENDAR_UI_KEY_MAP.put("attendanceStampHint", "calendar.ui.attendanceStampHint");
		CALENDAR_UI_KEY_MAP.put("attendanceTitle", "calendar.ui.attendanceTitle");
		CALENDAR_UI_KEY_MAP.put("communitySelectLabel", "calendar.ui.communitySelectLabel");
		CALENDAR_UI_KEY_MAP.put("close", "calendar.ui.close");
		CALENDAR_UI_KEY_MAP.put("prev", "calendar.ui.prev");
		CALENDAR_UI_KEY_MAP.put("next", "calendar.ui.next");
		// 헤더 언어·알림 아이콘과 같은 문구라 기존 키를 재사용한다.
		CALENDAR_UI_KEY_MAP.put("languageLabel", "layout.header.languageLabel");
		CALENDAR_UI_KEY_MAP.put("notificationsLabel", "layout.header.notificationsLabel");
	}

	private static final String[] CALENDAR_EVENT_TYPES = {
			"tv_broadcast", "youtube", "concert", "radio", "awards",
			"photo_magazine", "birthday", "other", "broadcast", "live", "ticket_open",
	};

	private static final String[] CALENDAR_WEEKDAYS = { "sun", "mon", "tue", "wed", "thu", "fri", "sat" };

	private static final String[] CALENDAR_NOTI_TITLES = { "live_start", "ticket_d1", "concert_day", "broadcast" };

	private static final String[] CALENDAR_CATEGORIES = {
			"post", "community_notice", "site_notice", "artist_comment", "comment", "media",
	};

	private static final String[] CALENDAR_EVENT_MESSAGES = { "live_start", "ticket_d1", "concert_day", "broadcast" };

	private static final String[] CALENDAR_RELATIVE_TIME = { "today", "yesterday", "daysAgo", "daysFuture" };

	@GetMapping(value = "/api/i18n/calendar", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public Map<String, Object> calendarMessages() {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Language language : Language.values()) {
			Locale locale = PreferredLocaleResolver.toLocale(language);

			Map<String, String> ui = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : CALENDAR_UI_KEY_MAP.entrySet()) {
				ui.put(entry.getKey(), messageSource.getMessage(entry.getValue(), null, locale));
			}

			Map<String, String> eventType = new LinkedHashMap<>();
			for (String type : CALENDAR_EVENT_TYPES) {
				eventType.put(type, messageSource.getMessage("calendar.eventType." + type, null, locale));
			}

			Map<String, String> weekday = new LinkedHashMap<>();
			for (String day : CALENDAR_WEEKDAYS) {
				weekday.put(day, messageSource.getMessage("calendar.weekday." + day, null, locale));
			}

			Map<String, String> notiTitle = new LinkedHashMap<>();
			for (String type : CALENDAR_NOTI_TITLES) {
				notiTitle.put(type, messageSource.getMessage("calendar.notiTitle." + type, null, locale));
			}

			Map<String, String> category = new LinkedHashMap<>();
			for (String type : CALENDAR_CATEGORIES) {
				category.put(type, messageSource.getMessage("calendar.category." + type, null, locale));
			}

			Map<String, String> eventMessage = new LinkedHashMap<>();
			for (String type : CALENDAR_EVENT_MESSAGES) {
				eventMessage.put(type, messageSource.getMessage("calendar.eventMessage." + type, null, locale));
			}

			Map<String, String> relativeTime = new LinkedHashMap<>();
			for (String key : CALENDAR_RELATIVE_TIME) {
				relativeTime.put(key, messageSource.getMessage("calendar.relativeTime." + key, null, locale));
			}

			Map<String, Object> bundle = new LinkedHashMap<>();
			bundle.put("ui", ui);
			bundle.put("eventType", eventType);
			bundle.put("weekday", weekday);
			bundle.put("notiTitle", notiTitle);
			bundle.put("category", category);
			bundle.put("eventMessage", eventMessage);
			bundle.put("relativeTime", relativeTime);

			result.put(language.name().toLowerCase(), bundle);
		}
		return result;
	}
}
