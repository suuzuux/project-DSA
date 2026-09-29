package megane6.weplanet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;

// AUTH-11: "계정당 동시 로그인 1개" 제한(SecurityConfig 의 maximumSessions(1))이 쓰는 세션 목록을 빈으로 꺼내 둔다.
// 폼 로그인 말고 코드로 직접 로그인시키는 곳(SocialLoginSessionSupport.loginAs - 소셜 로그인, 휴면 해제, 멤버 프로필
// 로그인)에서도 같은 목록에 등록해야 동시 로그인 제한이 똑같이 적용되기 때문.
// SecurityConfig 안에 두면 SecurityConfig ↔ OAuth2LoginSuccessHandler ↔ SocialLoginSessionSupport 가 서로를
// 필요로 하는 순환 참조가 생겨서 별도 설정 클래스로 뺐다.
@Configuration
public class SessionRegistryConfig {
	
	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}
}
