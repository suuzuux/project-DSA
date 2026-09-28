package megane6.weplanet.controller;

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

/**
 * shell.js(공통 헤더 보강/좌측 메뉴 드로어 오버레이)는 Thymeleaf 프래그먼트가 아니라 순수 클라이언트
 * JS라서 #{...} 메시지 표현식을 직접 쓸 수 없다. 그래서 shell.js가 페이지 로드 시 fetch로 이 API를
 * 한 번 불러가서, 현재 세션 로케일(PreferredLocaleResolver)에 맞는 문자열 묶음을 받아간다.
 */
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
			// SETTINGS-03 커밋3: 드로어 "커뮤니티 찾아보기"로 페이지에 없던 검색/가입 모달을 shell.js가
			// 직접 만들어 넣을 때 쓰는 문구. 템플릿(index.html, layout.html :: joinModal)과 같은 키를 재사용한다.
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
	};

	/**
	 * SETTINGS-03 커밋3: main.js 공용 다이얼로그와 커뮤니티 화면의 순수 클라이언트 JS
	 * (community-join/explore/posts/post-detail/live.js)가 쓰는 문구 묶음.
	 * shell.js와 같은 방식으로 현재 세션 로케일 문구만 내려주고, 각 JS는 WePlaNet.t(key, 한국어기본값)로 꺼내 쓴다.
	 * 템플릿과 같은 문구는 기존 키를 재사용하고, JS 전용 문구만 client.* 네임스페이스로 새로 만들었다.
	 */
	private static final String[] CLIENT_KEYS = {
			// main.js - 공용 다이얼로그 / 배너 / 회원가입 폼 검증(signup-id.html의 #signupForm)
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
	};

	@GetMapping(value = "/api/i18n/client", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public Map<String, String> clientMessages() {
		Map<String, String> messages = new LinkedHashMap<>();
		for (String key : CLIENT_KEYS) {
			// shellMessages()와 같은 이유로 args=null - {0} 자리표시자는 JS(WePlaNet.t)가 직접 치환한다.
			messages.put(key, messageSource.getMessage(key, null, LocaleContextHolder.getLocale()));
		}
		return messages;
	}

	@GetMapping(value = "/api/i18n/shell", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public Map<String, String> shellMessages() {
		Map<String, String> messages = new LinkedHashMap<>();
		for (String key : SHELL_KEYS) {
			// args=null 로 넘겨서 MessageFormat이 돌지 않게 함 - {0} 같은 자리표시자는
			// 그대로 문자열에 남아있어야 shell.js에서 닉네임 등을 직접 치환할 수 있다.
			messages.put(key, messageSource.getMessage(key, null, LocaleContextHolder.getLocale()));
		}
		return messages;
	}

	/**
	 * SETTINGS-03 커밋4: 캘린더 위젯(global-icons.js)이 갖고 있던 ko/en/ja/zh/fr/es 자체 6개 언어
	 * 딕셔너리를 이 API 기반 시스템으로 완전히 통합했다(zh/fr/es 는 제거, ko/ja/en 만 유지).
	 *
	 * 이 위젯은 언어 버튼을 누르면 페이지 새로고침 전에도 즉시 다른 언어로 다시 그려야 해서,
	 * shell.js처럼 "현재 세션 로케일 문구만" 내려주는 게 아니라 ko/ja/en 3개 언어 문구를
	 * 한 번에 다 내려준다. 응답 형태: { "ko": {...}, "ja": {...}, "en": {...} }
	 */
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
		// 헤더 언어/알림 아이콘의 aria-label과 동일한 문구라 새 키를 만들지 않고 재사용한다.
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
