package megane6.weplanet.controller;

import megane6.weplanet.controller.common.PolicyController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 Thymeleaf 본문과 메시지를 렌더링해 정책 링크·언어별 문서가 정상적으로 열리는지 확인한다.
class PolicyPageTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setup() {
		ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
		messages.setBasename("messages");
		messages.setDefaultEncoding("UTF-8");
		ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
		templates.setPrefix("templates/");
		templates.setSuffix(".html");
		templates.setCharacterEncoding("UTF-8");
		SpringTemplateEngine engine = new SpringTemplateEngine();
		engine.setTemplateResolver(templates);
		engine.setTemplateEngineMessageSource(messages);
		ThymeleafViewResolver views = new ThymeleafViewResolver();
		views.setTemplateEngine(engine);
		views.setCharacterEncoding("UTF-8");
		mockMvc = MockMvcBuilders.standaloneSetup(new PolicyController())
				.setViewResolvers(views)
				.setLocaleResolver(new AcceptHeaderLocaleResolver())
				.build();
	}

	@Test
	void allSamplePagesRenderTheirOwnContentInEachSupportedLanguage() throws Exception {
		Map<String, String[]> headings = Map.of(
				"operation", new String[]{"제1조 목적과 적용 범위", "第1条 目的と適用範囲", "Article 1. Purpose and scope"},
				"paid", new String[]{"제1조 유료서비스의 범위", "第1条 有料サービスの範囲", "Article 1. Paid services"},
				"youth", new String[]{"제1조 보호 원칙과 이용 연령", "第1条 保護原則と利用年齢", "Article 1. Protection and age limits"},
				"illegal-content", new String[]{"1. 신고·삭제 요청 대상", "1. 通報・削除依頼の対象", "1. What you can report"},
				"cookies", new String[]{"1. 쿠키와 브라우저 저장소", "1. Cookieとブラウザー保存領域", "1. Cookies and browser storage"},
				"cookie-settings", new String[]{"1. 현재 저장 기능의 상태", "1. 現在の保存機能", "1. Current storage status"});
		Locale[] locales = {Locale.KOREAN, Locale.JAPANESE, Locale.ENGLISH};
		for (var page : headings.entrySet()) {
			for (int i = 0; i < locales.length; i++) {
				mockMvc.perform(get("/policy/" + page.getKey()).locale(locales[i]))
						.andExpect(status().isOk())
						.andExpect(content().string(containsString(page.getValue()[i])))
						.andExpect(content().string(containsString("href=\"/policy/privacy\"")))
						.andExpect(content().string(containsString("href=\"/policy/terms\"")))
						.andExpect(content().string(containsString("mailto:admin4.wp@gmail.com")))
						.andExpect(content().string(containsString("/css/policy.css")))
						.andExpect(content().string(containsString("class=\"policy-toc\"")))
						.andExpect(content().string(containsString("class=\"policy-addendum\"")))
						.andExpect(effectiveDateInHeaderAndAddendum(locales[i]))
						.andExpect(content().string(not(containsString("policy-notice"))))
						.andExpect(content().string(not(containsString("support@weplanet.io"))));
			}
		}
	}

	@Test
	void existingPoliciesStillRenderInEachSupportedLanguage() throws Exception {
		for (String page : new String[]{"privacy", "terms"}) {
			for (Locale locale : new Locale[]{Locale.KOREAN, Locale.JAPANESE, Locale.ENGLISH}) {
				mockMvc.perform(get("/policy/" + page).locale(locale))
						.andExpect(status().isOk())
						.andExpect(content().string(containsString("id=\"art1\"")))
						.andExpect(content().string(containsString("/css/policy.css")))
						.andExpect(content().string(containsString("class=\"policy-addendum\"")))
						.andExpect(content().string(containsString("mailto:admin4.wp@gmail.com")))
						.andExpect(effectiveDateInHeaderAndAddendum(locale));
			}
		}
	}

	@Test
	void termsAndPrivacyReturnToThePageTheyWereOpenedFrom() throws Exception {
		Locale[] locales = {Locale.KOREAN, Locale.JAPANESE, Locale.ENGLISH};
		String[] homeLabels = {"← 홈으로", "← ホームへ", "← Back to home"};
		String[] settingsLabels = {"← 설정으로", "← 設定に戻る", "← Back to settings"};
		for (String page : new String[]{"terms", "privacy"}) {
			for (int i = 0; i < locales.length; i++) {
				mockMvc.perform(get("/policy/" + page).locale(locales[i]))
						.andExpect(status().isOk())
						.andExpect(content().string(containsString("href=\"/\"")))
						.andExpect(content().string(containsString(homeLabels[i])))
						.andExpect(content().string(not(containsString(settingsLabels[i]))));
				mockMvc.perform(get("/policy/" + page).param("from", "settings").locale(locales[i]))
						.andExpect(status().isOk())
						.andExpect(content().string(containsString("href=\"/settings\"")))
						.andExpect(content().string(containsString(settingsLabels[i])))
						.andExpect(content().string(not(containsString(homeLabels[i]))))
						.andExpect(content().string(containsString("href=\"/policy/terms?from=settings\"")))
						.andExpect(content().string(containsString("href=\"/policy/privacy?from=settings\"")));
			}
		}
	}

	private ResultMatcher effectiveDateInHeaderAndAddendum(Locale locale) {
		String date = switch (locale.getLanguage()) {
			case "ja" -> "2026年9月16日";
			case "en" -> "September 16, 2026";
			default -> "2026년 9월 16일";
		};
		return result -> assertEquals(2,
				result.getResponse().getContentAsString().split(Pattern.quote(date), -1).length - 1,
				"상단 시행일자와 부칙이 같은 날짜여야 합니다.");
	}

	@Test
	void unknownPolicyIsNotFoundAndUnsupportedLanguageUsesKoreanSample() throws Exception {
		mockMvc.perform(get("/policy/not-a-policy")).andExpect(status().isNotFound());
		mockMvc.perform(get("/policy/operation").locale(Locale.FRENCH))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("제1조 목적과 적용 범위")));
	}
}
