package megane6.weplanet.security;

import jakarta.servlet.http.Cookie;
import megane6.weplanet.domain.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SocialLoginSessionSupportTest {

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	// 코드로 직접 로그인시킬 때도(회원가입 자동 로그인, 소셜 로그인 등) 폼 로그인처럼
	// 세션 id 를 바꾸고, 로그인 전에 쓰던 CSRF 토큰을 버려야 한다 (FIX-02)
	@Test
	void loginAsReplacesSessionIdAndDiscardsCsrfToken() {
		SocialLoginSessionSupport support = new SocialLoginSessionSupport(
				new SessionRegistryImpl(), CookieCsrfTokenRepository.withHttpOnlyFalse());
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/signup");
		request.setCookies(new Cookie("XSRF-TOKEN", "token-before-login"));
		String sessionIdBeforeLogin = request.getSession(true).getId();
		MockHttpServletResponse response = new MockHttpServletResponse();
		User user = User.createFan("newfan01", "encoded", "이름", "닉네임", "newfan01@weplanet.test");

		support.loginAs(user, request, response);

		assertEquals("newfan01", SecurityContextHolder.getContext().getAuthentication().getName());
		assertNotEquals(sessionIdBeforeLogin, request.getSession(false).getId());
		// 로그인 전 토큰은 빈 값 + 즉시 만료 쿠키로 지워지고, 다음 요청에서 CsrfCookieFilter 가 새 토큰을 내려준다
		Cookie csrfCookie = response.getCookie("XSRF-TOKEN");
		assertNotNull(csrfCookie);
		assertEquals("", csrfCookie.getValue());
		assertEquals(0, csrfCookie.getMaxAge());
	}
}
