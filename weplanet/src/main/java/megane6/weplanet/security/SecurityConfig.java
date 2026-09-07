package megane6.weplanet.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private static final List<String> PUBLIC_URLS = List.of(
            "/",
            "/home",
            "/signup",
            "/signup/id",
            "/signup/email/**",
            "/signup/username/**",
            "/find-id",
            "/find-id/**",
            "/find-password",
            "/find-password/**",
            "/login",
            "/login/id",
            "/portal/login",
            "/admin/login",
            "/api/schedules",
            "/api/notifications",
            "/api/artists",
            "/posts/**",
            "/chat/**",
            "/ws-chat/**",
            "/dev/**",
            "/uploads/**",
            "/community/**",
            "/board/**",
            "/notices",
            "/notices/**",
            "/shop",
            "/shop/**",
            "/membership",
            "/partnership",
            "/css/**",
            "/js/**",
            "/img/**",
            "/signup-wireframe",
            "/login-wireframe",
            "/oauth2/authorization/**",
            "/login/oauth2/code/**",
            "/social-login/**"
    );
    
    private final LoginSuccessHandler loginSuccessHandler;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final SocialSignupReauthAuthorizationRequestResolver socialSignupReauthAuthorizationRequestResolver;
    private final ProfileCompletionRequiredFilter profileCompletionRequiredFilter;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 관리자 영역은 ADMIN 역할만. 이 줄이 없으면 팬 계정으로도 들어와짐
                        .requestMatchers(PUBLIC_URLS.toArray(String[]::new)).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(portalAwareFailureHandler())
                        .permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .successHandler(oAuth2LoginSuccessHandler)
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(socialSignupReauthAuthorizationRequestResolver))
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/")
                )
                .sessionManagement(session -> session
                        .invalidSessionUrl("/login?expired=true")
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/login?duplicateLogin=true")
                )
                .addFilterAfter(profileCompletionRequiredFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    private AuthenticationFailureHandler portalAwareFailureHandler() {
        return (request, response, exception) -> {
            if ("true".equals(request.getParameter("adminLogin"))) {
                response.sendRedirect("/admin/login?error");
                return;
            }
            if ("true".equals(request.getParameter("portalLogin"))) {
                response.sendRedirect("/portal/login?error");
                return;
            }
            response.sendRedirect("/login?error");
        };
        
    }
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}