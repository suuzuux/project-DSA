package megane6.weplanet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

// FIX-02: CSRF 토큰 저장소(XSRF-TOKEN 쿠키)를 빈으로 꺼내 둔다.
// SecurityConfig(폼 로그인 등)와 SocialLoginSessionSupport.loginAs(소셜 로그인, 휴면 해제, 회원가입 자동 로그인 등
// 코드로 직접 로그인시키는 곳)가 같은 저장소를 써야, 로그인할 때 토큰을 똑같이 새로 바꿔 줄 수 있다.
// SecurityConfig 안에 두면 SessionRegistryConfig 와 같은 이유로 순환 참조가 생겨서 별도 설정 클래스로 뺐다.
@Configuration
public class CsrfTokenRepositoryConfig {

	@Bean
	public CsrfTokenRepository csrfTokenRepository() {
		// csrf.js 가 쿠키 값을 읽어 헤더에 넣어야 하므로 HttpOnly 를 끈다
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}
}
