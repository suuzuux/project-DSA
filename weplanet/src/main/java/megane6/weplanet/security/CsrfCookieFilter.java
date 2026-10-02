package megane6.weplanet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// FIX-02: 모든 응답에 XSRF-TOKEN 쿠키가 내려가도록 CSRF 토큰을 미리 꺼내 두는 필터.
// 스프링 시큐리티는 토큰을 "누군가 실제로 꺼내 쓸 때" 만들고 쿠키로 내려준다(지연 로딩).
// Thymeleaf 폼(th:action)이 없는 페이지는 토큰을 꺼내는 곳이 없어서 쿠키가 안 생기고,
// 그러면 csrf.js 가 fetch 헤더에 넣을 값이 없어 POST/PUT/DELETE 요청이 403 으로 막힌다.
// 그래서 CsrfFilter 다음에 이 필터를 두고 매 요청마다 토큰을 한 번 꺼내서 쿠키가 항상 있게 한다.
public class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			// 값을 읽는 것만으로 토큰이 만들어지고 CookieCsrfTokenRepository 가 쿠키를 응답에 싣는다
			csrfToken.getToken();
		}
		filterChain.doFilter(request, response);
	}
}
