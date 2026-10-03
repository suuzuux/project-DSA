package megane6.weplanet.security;

import org.springframework.security.core.AuthenticationException;

// 로그인 시도가 너무 많아 잠긴 아이디/IP 로 로그인하려 할 때 (LoginAttemptService, LoginAttemptFilter).
// 로그인 실패 처리기(SecurityConfig.portalAwareFailureHandler)가 이 예외를 보고 "잠시 후 다시 시도" 안내 화면으로 보낸다.
public class LoginAttemptsExceededException extends AuthenticationException {

	public LoginAttemptsExceededException() {
		super("로그인 시도가 너무 많아 잠시 로그인할 수 없습니다.");
	}
}
