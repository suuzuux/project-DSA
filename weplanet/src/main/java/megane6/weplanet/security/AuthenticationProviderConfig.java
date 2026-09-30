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

// AUTH-11: 아이디/비밀번호 로그인 인증 방식을 직접 등록한다.
// 스프링 기본값은 "비밀번호를 확인하기 전에" 계정 사용 가능 여부(enabled)를 먼저 검사해서, 비밀번호를 아무렇게나
// 넣어도 휴면 계정이면 DisabledException → 로그인 화면에 "휴면 계정입니다" 안내가 떴다. 그래서 아이디만 알면
// 그 계정이 휴면인지 알 수 있었다(휴면 해제 공격 대상 수집에 쓰일 수 있음).
// 여기서는 비밀번호가 맞은 뒤에(postAuthenticationChecks) enabled 를 검사하도록 순서를 바꾼다.
// 단, 비밀번호가 아직 없는 계정(입점 승인 후 초대 링크로 비밀번호를 정하기 전인 소속사/아티스트)은 비교할 비밀번호가
// 없으므로 예전처럼 먼저 DisabledException 을 던져서 "활성화 메일을 확인하세요" 안내가 그대로 나오게 한다.
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
