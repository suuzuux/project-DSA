package megane6.weplanet.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
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
            "/login/reactivate",
            "/login/reactivate/**",
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
    private final RoleAwareLogoutSuccessHandler roleAwareLogoutSuccessHandler;
    private final SocialSignupReauthAuthorizationRequestResolver socialSignupReauthAuthorizationRequestResolver;
    private final ProfileCompletionRequiredFilter profileCompletionRequiredFilter;
    private final UserRepository userRepository;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS.toArray(String[]::new)).permitAll()
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
                        .logoutSuccessHandler(roleAwareLogoutSuccessHandler)
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
                String portalRole = request.getParameter("portalRole");
                String roleQs = (portalRole != null && !portalRole.isBlank())
                        ? "&role=" + portalRole.trim().toUpperCase()
                        : "";
                response.sendRedirect("/portal/login?error" + roleQs);
                return;
            }
            if (exception instanceof DisabledException) {
                String username = request.getParameter("username");
                boolean dormant = username != null && userRepository.findByUsername(username)
                        .map(u -> u.getStatus() == UserStatus.DORMANT)
                        .orElse(false);
                if (dormant) {
                    response.sendRedirect("/login?dormant=true");
                    return;
                }
                // WITHDRAWN/SUSPENDED는 구분 안 하고 일반 에러로 - 탈퇴 여부를 로그인 화면에서 노출 안 하려는 의도
            }
            response.sendRedirect("/login?error");
        };
        
    }
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}