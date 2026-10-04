package megane6.weplanet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;

// "계정당 동시 로그인 1개" 제한이 쓰는 세션 목록 - 코드로 직접 로그인시키는 곳(SocialLoginSessionSupport.loginAs)도 같은 목록을 쓴다.
// SecurityConfig 안에 두면 순환 참조가 생겨서 별도 설정 클래스로 뺐다.
@Configuration
public class SessionRegistryConfig {
	
	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}
}
