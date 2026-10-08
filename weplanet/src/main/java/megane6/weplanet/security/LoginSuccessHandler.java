package megane6.weplanet.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.artist.GroupMemberRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.portal.AgencyEnrollmentService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final AgencyEnrollmentService agencyEnrollmentService;
	private final LocaleResolver localeResolver;
	private final GroupMemberRepository groupMemberRepository;
	private final LoginAttemptService loginAttemptService;

	@PostConstruct
	public void init() {
		setDefaultTargetUrl("/");
		setAlwaysUseDefaultTargetUrl(true);
	}

	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
										Authentication authentication) throws IOException, ServletException {
		AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
		// 로그인 성공 - 실패 횟수 초기화
		loginAttemptService.recordSuccess(principal.getUsername());
		boolean portalLogin = "true".equals(request.getParameter("portalLogin"));
		boolean adminLogin = "true".equals(request.getParameter("adminLogin"));
		// 포털 로그인에서 고른 언어 (세션을 버리기 전에 읽어 둠).
		Locale chosenLocale = portalLogin && PreferredLocaleResolver.hasExplicitChoice(request)
				? localeResolver.resolveLocale(request)
				: null;

		// 관리자는 /admin/login 의 2단계 인증을 반드시 거친다.
		if (adminLogin) {
			clearAuthentication(request);
			getRedirectStrategy().sendRedirect(request, response, "/admin/login?error");
			return;
		} else if (portalLogin) {
			// 포털 로그인: 선택 탭과 실제 역할이 같아야 한다.
			String roleName = principal.getRoleName();

			// 포털 대상이 아니면 팬 로그인으로
			if (!"ROLE_ARTIST".equals(roleName) && !"ROLE_AGENCY".equals(roleName)) {
				clearAuthentication(request);
				getRedirectStrategy().sendRedirect(request, response, "/login?error=portal");
				return;
			}

			String expected = expectedPortalRole(request.getParameter("portalRole"));
			if (expected != null && !expected.equals(roleName)) {
				clearAuthentication(request);
				String tab = "ROLE_ARTIST".equals(expected) ? "ARTIST" : "AGENCY";
				getRedirectStrategy().sendRedirect(request, response,
						"/portal/login?error=role&role=" + tab);
				return;
			}
		} else {
			// 팬 로그인은 아티스트·소속사·멤버·관리자 계정을 막는다 (일반 실패와 같은 화면).
			String roleName = principal.getRoleName();
			if ("ROLE_ARTIST".equals(roleName) || "ROLE_AGENCY".equals(roleName)
					|| "ROLE_ARTIST_MEMBER".equals(roleName) || "ROLE_ADMIN".equals(roleName)) {
				clearAuthentication(request);
				getRedirectStrategy().sendRedirect(request, response, "/login/id?error");
				return;
			}
		}
		
		// 멤버가 있는 그룹 계정은 로그인시키지 않고 프로필 선택으로 보낸다 (대기 그룹 id 만 새 세션에 남김).
		if ("ROLE_ARTIST".equals(principal.getRoleName())
				&& groupMemberRepository.existsByGroupIdAndLeftAtIsNull(principal.getId())) {
			clearAuthentication(request);
			ArtistProfileLoginSupport.begin(request.getSession(true), principal.getId());
			// 새 세션에 화면 언어를 다시 넣는다 (선택 언어 또는 그룹 선호 언어).
			if (chosenLocale != null) {
				localeResolver.setLocale(request, response, chosenLocale);
				PreferredLocaleResolver.markExplicitChoice(request);
			} else {
				PreferredLocaleResolver.clearExplicitChoice(request);
				userRepository.findOneById(principal.getId()).ifPresent(group ->
						localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(group.getPreferredLanguage())));
			}
			getRedirectStrategy().sendRedirect(request, response, "/portal/profiles");
			return;
		}

		userRepository.findOneById(principal.getId()).ifPresent(user -> {
			// 행 잠금 대기를 피하려고 소속사 자동 가입을 로그인 기록보다 먼저 한다.
			if (user.getRole() == Role.AGENCY) {
				agencyEnrollmentService.enrollManagedArtists(user);
			}
			user.recordLogin();
			if (user.getRole() == Role.ADMIN) {
				// 관리자는 한국어 고정
				localeResolver.setLocale(request, response, Locale.KOREAN);
			} else if (chosenLocale != null) {
				// 포털에서 고른 언어를 유지하고 선호 언어로 저장한다.
				user.changePreferredLanguage(PreferredLocaleResolver.toLanguage(chosenLocale));
				localeResolver.setLocale(request, response, chosenLocale);
				PreferredLocaleResolver.clearExplicitChoice(request);
			} else {
				// 팬: 계정 선호 언어로 화면 언어를 맞춘다.
				localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(user.getPreferredLanguage()));
			}
		});

		getRedirectStrategy().sendRedirect(request, response, RoleHomeRedirects.pathFor(principal));
	}

	/** 인증 해제 후 새 세션을 열고 화면 언어를 이어 준다 (/login?expired 방지). */
	private static void clearAuthentication(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		PreferredLocaleResolver.invalidateSessionKeepingLocale(request);
	}

	private static String expectedPortalRole(String portalRole) {
		if (portalRole == null || portalRole.isBlank()) {
			return null;
		}
		return switch (portalRole.trim().toUpperCase()) {
			case "ARTIST" -> "ROLE_ARTIST";
			case "AGENCY" -> "ROLE_AGENCY";
			default -> null;
		};
	}
}
