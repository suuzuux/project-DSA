package megane6.weplanet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.crypto.password.PasswordEncoder;

// 아이디·비밀번호 인증 설정 - 비밀번호 확인 뒤에 계정 상태를 검사한다 (휴면 여부 노출 방지).
@Configuration
public class AuthenticationProviderConfig {
	
	@Bean
	public DaoAuthenticationProvider daoAuthenticationProvider(AuthenticatedUserDetailsService userDetailsService,
															   PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		provider.setPreAuthenticationChecks(preChecks());
		provider.setPostAuthenticationChecks(postChecks());
		return provider;
	}
	
	// 비밀번호 확인 전: 잠김·만료, 비밀번호 없는 계정만 확인
	private static UserDetailsChecker preChecks() {
		return user -> {
			if (!user.isAccountNonLocked()) {
				throw new LockedException("잠긴 계정입니다.");
			}
			if (!user.isAccountNonExpired()) {
				throw new AccountExpiredException("만료된 계정입니다.");
			}
			if (!user.isEnabled() && user.getPassword() == null) {
				throw new DisabledException("비밀번호를 아직 설정하지 않은 계정입니다.");
			}
		};
	}
	
	// 비밀번호 확인 후: 휴면·정지 상태를 알려도 된다.
	private static UserDetailsChecker postChecks() {
		return user -> {
			if (!user.isEnabled()) {
				throw new DisabledException("사용할 수 없는 상태의 계정입니다.");
			}
			if (!user.isCredentialsNonExpired()) {
				throw new CredentialsExpiredException("비밀번호가 만료되었습니다.");
			}
		};
	}
}
