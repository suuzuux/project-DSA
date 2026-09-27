package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
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
	};

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
}
