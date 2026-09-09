package megane6.weplanet.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.portal.AgencyEnrollmentService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final AgencyEnrollmentService agencyEnrollmentService;

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

		// 포털 로그인: 아티스트/에이전시 전용. 선택 탭과 실제 역할이 일치해야 함.
		if ("true".equals(request.getParameter("portalLogin"))) {
			String roleName = principal.getRoleName();

			// 팬·관리자 등 포털 대상이 아닌 계정 → 메인 팬 로그인으로
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
		}

		userRepository.findOneById(principal.getId()).ifPresent(user -> {
			user.recordLogin();
			if (user.getRole() == Role.AGENCY) {
				agencyEnrollmentService.enrollManagedArtists(user);
			}
		});

		getRedirectStrategy().sendRedirect(request, response, RoleHomeRedirects.pathFor(principal));
	}

	/** 인증 해제 후 새 세션을 열어 invalidSessionUrl(/login?expired)로 튕기지 않게 함 */
	private static void clearAuthentication(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		request.getSession(true);
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
