package megane6.weplanet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // 모든 요청을 인증 없이 허용
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                // 기본 로그인 폼 비활성화
                .formLogin(form -> form.disable())
                // 기본 HTTP Basic 인증 비활성화
                .httpBasic(basic -> basic.disable())
                // CSRF 보호 비활성화 (개발 초기 단계에서만, 나중에 로그인 붙이면 다시 검토)
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}