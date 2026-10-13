package megane6.weplanet.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.controller.common.auth.AuthController;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.web.CommunitySlugForwardFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
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
            "/language",
            "/login/reactivate",
            "/login/reactivate/**",
            "/portal/login",
            // 아티스트 프로필 선택 (대기 그룹 id 로만 접근)
            "/portal/profiles",
            "/portal/profiles/**",
            "/admin/login",
            "/api/schedules",
            "/api/notifications",
            "/api/site-notices",
            // 메인 배너 번역문 (비로그인 포함)
            "/api/main-banners",
            // shell.js 다국어 문구 API (비로그인 화면에서도 사용)
            "/api/i18n/**",
            // 햄버거 메뉴 커뮤니티 목록
            "/api/side-menu/communities",
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
            // 토스 입금 웹훅 (secret 으로 검증)
            "/payments/toss/webhook",
            "/membership",
            "/partnership",
            // 계정 활성화 링크 (로그인 전)
            "/partner/activate",
            "/policy/**",
            "/css/**",
            "/js/**",
            "/img/**",
            // 브라우저 탭 아이콘
            "/favicon.ico",
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
    private final UserRepository userRepository;
    private final CommunitySlugForwardFilter communitySlugForwardFilter;
    private final SessionRegistry sessionRegistry; // SessionRegistryConfig 참고
    private final LoginAttemptService loginAttemptService; // 로그인 비밀번호 대입 방어

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        AuthenticationFailureHandler failureHandler = portalAwareFailureHandler();
        http
                // 로그인 처리 직전에 잠긴 아이디·IP 확인
                .addFilterBefore(new LoginAttemptFilter(loginAttemptService, failureHandler),
                        UsernamePasswordAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 관리자 로그인 2단계 인증 경로만 공개 (나머지 /admin/** 는 ADMIN).
                        .requestMatchers(
                                "/admin/login",
                                "/admin/login/code",
                                "/admin/login/verify"
                        ).permitAll()
                        
                        // /chat/** 공개 규칙보다 먼저 검사한다.
                        .requestMatchers(
                                "/admin/**",
                                "/chat/admin/**"
                        ).hasRole("ADMIN")
                        
                        .requestMatchers(
                                PUBLIC_URLS.toArray(String[]::new)
                        ).permitAll()

                        // 등록된 커뮤니티 영문 주소만 /community/** 와 같은 범위로 공개한다.
                        .requestMatchers(request -> communitySlugForwardFilter.forwardTargetOf(request).isPresent())
                        .permitAll()

                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(failureHandler)
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
                        // 로그아웃 전후로 화면 언어를 유지한다.
                        .addLogoutHandler(roleAwareLogoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(roleAwareLogoutSuccessHandler)
                )
                .sessionManagement(session -> session
                        .invalidSessionUrl("/login?expired=true")
                        .maximumSessions(1)
                        .sessionRegistry(sessionRegistry)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/login?duplicateLogin=true")
                );
        
        return http.build();
    }

    private AuthenticationFailureHandler portalAwareFailureHandler() {
        return (request, response, exception) -> {
            // 비밀번호가 틀린 경우만 센다 (없는 아이디는 IP 만).
            String attemptedUsername = request.getParameter("username");
            if (exception instanceof BadCredentialsException) {
                boolean accountExists = attemptedUsername != null && !attemptedUsername.isBlank()
                        && userRepository.existsByUsername(attemptedUsername.trim());
                loginAttemptService.recordFailure(accountExists ? attemptedUsername : null, request.getRemoteAddr());
            }
            // 이번 실패로 잠겼으면 바로 잠금 안내를 보여준다.
            boolean locked = exception instanceof LoginAttemptsExceededException
                    || loginAttemptService.isBlocked(attemptedUsername, request.getRemoteAddr());

            if ("true".equals(request.getParameter("adminLogin"))) {
                response.sendRedirect(locked ? "/admin/login?locked" : "/admin/login?error");
                return;
            }
            if ("true".equals(request.getParameter("portalLogin"))) {
                String portalRole = request.getParameter("portalRole");
                String roleQs = (portalRole != null && !portalRole.isBlank())
                        ? "&role=" + portalRole.trim().toUpperCase()
                        : "";

                if (locked) {
                    response.sendRedirect("/portal/login?error=locked" + roleQs);
                    return;
                }

                // 승인됐지만 아직 비밀번호를 설정하지 않은 계정
                if (exception instanceof DisabledException) {
                    String username = request.getParameter("username");
                    boolean pendingActivation = username != null && userRepository.findByUsername(username)
                            .map(u -> u.getStatus() == UserStatus.PENDING_ACTIVATION)
                            .orElse(false);
                    
                    if (pendingActivation) {
                        response.sendRedirect("/portal/login?error=pending" + roleQs);
                        return;
                    }
                }
                
                response.sendRedirect("/portal/login?error" + roleQs);
                return;
            }
            if (locked) {
                response.sendRedirect("/login/id?locked");
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
                // 탈퇴·정지는 구분하지 않고 일반 오류로 보여준다.
            }
            // 없는 아이디로 실패하면 회원가입을 권한다 (5회째 확인창, 아이디는 세션에 보관).
            if (exception instanceof BadCredentialsException) {
                String username = request.getParameter("username");
                String trimmed = username == null ? "" : username.trim();
                if (!trimmed.isEmpty() && !userRepository.existsByUsername(trimmed)) {
                    jakarta.servlet.http.HttpSession session = request.getSession(true);
                    session.setAttribute(AuthController.SESSION_KEY_LOGIN_NOT_FOUND_USERNAME, trimmed);
                    Object prev = session.getAttribute(AuthController.SESSION_KEY_LOGIN_NOT_FOUND_COUNT);
                    int count = (prev instanceof Integer n ? n : 0) + 1;
                    if (count >= AuthController.LOGIN_NOT_FOUND_ASK_AT) {
                        session.removeAttribute(AuthController.SESSION_KEY_LOGIN_NOT_FOUND_COUNT);
                        response.sendRedirect("/login/id?notFound&ask");
                    } else {
                        session.setAttribute(AuthController.SESSION_KEY_LOGIN_NOT_FOUND_COUNT, count);
                        response.sendRedirect("/login/id?notFound");
                    }
                    return;
                }
            }
            // 아이디·비밀번호 폼과 오류 문구가 있는 /login/id 로 보낸다.
            response.sendRedirect("/login/id?error");
        };
        
    }
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
