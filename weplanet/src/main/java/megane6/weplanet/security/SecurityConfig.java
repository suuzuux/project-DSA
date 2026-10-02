package megane6.weplanet.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.controller.AuthController;
import megane6.weplanet.repository.UserRepository;
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
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
            // 아티스트 2단계 로그인(프로필 선택) - 로그인 전 화면. 세션의 대기 그룹 id 로만 접근 가능
            "/portal/profiles",
            "/portal/profiles/**",
            "/admin/login",
            "/api/schedules",
            "/api/notifications",
            "/api/site-notices",
            // SETTINGS-03: shell.js(공통 헤더/사이드바)가 로그인 여부와 무관하게 fetch로 받아가는
            // 다국어 문자열 API - 비로그인 화면(메인 등)에서도 셸이 그려지므로 공개해야 한다
            "/api/i18n/**",
            // 햄버거 메뉴 커뮤니티 목록 - 비로그인도 전체 커뮤니티는 볼 수 있다
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
            // 해시태그 총공 공개 페이지 - 홈 배너로 비로그인도 들어온다
            "/events/**",
            "/shop",
            "/shop/**",
            // 토스 입금 웹훅 - 토스 서버가 호출하므로 로그인 없음 (secret 값으로 검증)
            "/payments/toss/webhook",
            "/membership",
            "/partnership",
            // 입점 승인 메일의 계정 활성화 링크 - 아직 로그인할 수 없는 사용자가 들어온다
            "/partner/activate",
            "/policy/**",
            "/css/**",
            "/js/**",
            "/img/**",
            // 브라우저 탭 아이콘 - 로그인 전 화면에서도 브라우저가 자동으로 요청한다
            "/favicon.ico",
            "/signup-wireframe",
            "/login-wireframe",
            "/oauth2/authorization/**",
            "/login/oauth2/code/**",
            "/social-login/**",
            // FIX-02: CSRF 토큰 검증 실패 안내 화면 - 로그인 전 폼(회원가입 등)에서도 보일 수 있다
            "/csrf-expired"
    );

    // FIX-02: CSRF 검사에서 빼는 주소. 우리 화면이 아니라 외부 서버가 직접 호출해서 토큰을 붙일 수 없는 곳만 둔다.
    // (여기 넣는 주소는 반드시 다른 방법으로 요청을 검증해야 한다 - 토스 웹훅은 secret 값으로 검증)
    private static final List<String> CSRF_IGNORED_URLS = List.of(
            "/payments/toss/webhook"
    );
    
    private final LoginSuccessHandler loginSuccessHandler;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final RoleAwareLogoutSuccessHandler roleAwareLogoutSuccessHandler;
    private final SocialSignupReauthAuthorizationRequestResolver socialSignupReauthAuthorizationRequestResolver;
    private final UserRepository userRepository;
    private final CommunitySlugForwardFilter communitySlugForwardFilter;
    private final SessionRegistry sessionRegistry; // AUTH-11: SessionRegistryConfig 참고
    private final CsrfAccessDeniedHandler csrfAccessDeniedHandler; // FIX-02
    private final CsrfTokenRepository csrfTokenRepository; // FIX-02: CsrfTokenRepositoryConfig 참고

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // FIX-02: CSRF 방어를 켠다. POST/PUT/PATCH/DELETE 요청은 우리 화면이 받은 토큰을 같이 보내야만 통과한다.
                // 토큰은 XSRF-TOKEN 쿠키로 내려주고(JS 가 읽을 수 있게 HttpOnly 아님), 아래 방식 모두 받아준다.
                //  - Thymeleaf 폼(th:action) : 숨은 _csrf 필드가 자동으로 들어간다
                //  - fetch                  : /js/csrf.js 가 쿠키 값을 X-XSRF-TOKEN 헤더에 넣는다
                //  - JS 가 만든 폼(shell.js) : /js/csrf.js 가 제출 직전에 _csrf 필드를 넣는다
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new WeplanetCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(CSRF_IGNORED_URLS.toArray(String[]::new))
                )
                // 토큰 쿠키가 모든 페이지에서 내려가도록 (CsrfCookieFilter 주석 참고)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(csrfAccessDeniedHandler)
                )
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 관리자 로그인 화면은 누구나 접근 가능
                        .requestMatchers("/admin/login").permitAll()
                        
                        // 넓은 /chat/** 공개 규칙보다 먼저 검사해야 함
                        .requestMatchers(
                                "/admin/**",
                                "/chat/admin/**"
                        ).hasRole("ADMIN")
                        
                        .requestMatchers(
                                PUBLIC_URLS.toArray(String[]::new)
                        ).permitAll()

                        // 커뮤니티 영문 주소(/kiikii, /kiikii/fan) - /community/** 와 같은 공개 범위.
                        // 등록된 영문명일 때만 공개하고, 아니면 아래 anyRequest 규칙을 그대로 탄다
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
                        // 세션을 버리기 전에 화면 언어를 읽어 두고, 로그아웃 후 새 세션에 다시 넣는다 (RoleAwareLogoutSuccessHandler)
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
            if ("true".equals(request.getParameter("adminLogin"))) {
                response.sendRedirect("/admin/login?error");
                return;
            }
            if ("true".equals(request.getParameter("portalLogin"))) {
                String portalRole = request.getParameter("portalRole");
                String roleQs = (portalRole != null && !portalRole.isBlank())
                        ? "&role=" + portalRole.trim().toUpperCase()
                        : "";
                
                // 입점 승인은 됐지만, 아직 메일 링크로 비밀번호를 설정하지 않은 소속사 계정
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
            // 입력한 아이디로 가입된 계정이 아예 없으면 회원가입을 권한다 (팬 로그인 화면만).
            // 오타일 수도 있어서 바로 가입 화면으로 보내지 않는다. 1~4회째는 로그인 폼 아래에 "가입된 아이디가 없습니다.
            // 회원가입하시겠습니까?" 안내만 보여주고, 5회째에 확인창(예/아니오)을 띄운 뒤 횟수를 다시 센다.
            // 회원가입으로 넘어가면 입력한 아이디가 채워진 가입 화면이 열린다(AuthController.signupForm).
            // 아이디는 URL 대신 세션에 잠깐 담는다.
            // (아이디 존재 여부는 가입 화면의 "중복 확인"으로도 알 수 있는 정보라 여기서 알려줘도 새로 드러나는 것은 없다)
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
            // "/login"은 SNS/아이디 선택 화면(login-wireframe)이라 에러 문구가 없다.
            // 실제 아이디/비밀번호 폼과 에러 문구는 "/login/id"(login-id.html)에 있으므로 거기로 보내야 한다.
            response.sendRedirect("/login/id?error");
        };
        
    }
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}