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

// 아이디/비밀번호 로그인 인증 설정 - 비밀번호가 맞은 뒤에 계정 상태(휴면 등)를 검사해서, 아이디만으로 휴면 여부가 드러나지 않게 한다.
// 단, 비밀번호가 아직 없는 계정(초대 링크로 비밀번호를 정하기 전)은 먼저 "활성화 메일 확인" 안내를 띄운다.
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
	
	// 비밀번호 확인 전: 잠김/만료만 확인하고, enabled 는 "비밀번호가 없는 계정"일 때만 여기서 확인
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
	
	// 비밀번호 확인 후: 여기까지 왔다면 비밀번호는 맞았으므로 휴면/정지 같은 상태를 알려줘도 된다
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
