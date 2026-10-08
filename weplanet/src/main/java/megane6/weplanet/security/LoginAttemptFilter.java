package megane6.weplanet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 로그인 직전에 잠긴 아이디·IP 인지 확인한다 (UsernamePasswordAuthenticationFilter 앞).
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
