package megane6.weplanet.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.portal.AgencyEnrollmentService;
import org.springframework.security.core.Authentication;
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

		// 포털 로그인: 화면에서 고른 유형(아티스트/에이전시)과 실제 계정 역할이 같아야 함
		if ("true".equals(request.getParameter("portalLogin"))) {
			String portalRole = request.getParameter("portalRole");
			String expected = expectedPortalRole(portalRole);
			if (expected != null && !expected.equals(principal.getRoleName())) {
				org.springframework.security.core.context.SecurityContextHolder.clearContext();
				var session = request.getSession(false);
				if (session != null) {
					session.invalidate();
				}
				getRedirectStrategy().sendRedirect(request, response, "/portal/login?error=role&role="
						+ ("ROLE_ARTIST".equals(expected) ? "ARTIST" : "AGENCY"));
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
