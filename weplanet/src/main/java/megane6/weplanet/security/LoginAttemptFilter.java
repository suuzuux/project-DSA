package megane6.weplanet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 아이디·비밀번호 로그인(POST /login)을 처리하기 직전에, 잠긴 아이디나 IP 인지 먼저 확인한다 (LoginAttemptService).
// 잠겨 있으면 비밀번호를 확인하지 않고 바로 로그인 실패 처리기로 넘긴다 - 잠긴 동안에는 비밀번호가 맞아도 들어올 수 없다.
// SecurityConfig 에서 UsernamePasswordAuthenticationFilter 바로 앞에 끼운다 (빈으로 등록하지 않음 - 모든 요청에 붙지 않게).
public class LoginAttemptFilter extends OncePerRequestFilter {

	private final LoginAttemptService loginAttemptService;
	private final AuthenticationFailureHandler failureHandler;

	public LoginAttemptFilter(LoginAttemptService loginAttemptService, AuthenticationFailureHandler failureHandler) {
		this.loginAttemptService = loginAttemptService;
		this.failureHandler = failureHandler;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (isLoginSubmit(request)
				&& loginAttemptService.isBlocked(request.getParameter("username"), request.getRemoteAddr())) {
			failureHandler.onAuthenticationFailure(request, response, new LoginAttemptsExceededException());
			return;
		}
		filterChain.doFilter(request, response);
	}

	private static boolean isLoginSubmit(HttpServletRequest request) {
		return "POST".equalsIgnoreCase(request.getMethod())
				&& "/login".equals(request.getRequestURI().substring(request.getContextPath().length()));
	}
}
