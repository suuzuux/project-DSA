package megane6.weplanet.security;

import org.springframework.security.core.AuthenticationException;

// 로그인 시도 초과로 잠긴 아이디·IP (실패 처리기가 잠금 안내로 보냄).
public class LoginAttemptsExceededException extends AuthenticationException {

	public LoginAttemptsExceededException() {
		super("로그인 시도가 너무 많아 잠시 로그인할 수 없습니다.");
	}
}
