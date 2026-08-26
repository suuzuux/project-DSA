package megane6.weplanet.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    // TODO: AUTH-07(로그인 세션 연동) 완료되면 아래 개발용 임시 허용 항목들 제거하고
    // PostController/ChatController의 getUserOrThrow()를 실제 로그인 세션 기반으로 교체할 것.
    // (테스트 계정 비밀번호가 아직 더미값이라 실제 로그인이 안 되는 동안, 게시판/채팅 개발·시연을 위해 임시로 열어둠 - 형준님 확인 완료)
    private static final List<String> PUBLIC_URLS = List.of(
            "/",
            "/home",
            "/signup",
            "/login",
            "/posts/**",
            "/chat/**",
            "/ws-chat/**",
            "/dev/**",
            "/uploads/**",
            "/community/**",
            "/board/**",
            "/css/**",
            "/js/**",
            "/img/**"
    );
    
    private final LoginSuccessHandler loginSuccessHandler;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/community/*/project",
                                "/community/*/project/**"
                        ).authenticated()
                        .requestMatchers(PUBLIC_URLS.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/")
                );
        
        return http.build();
    }
    
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}