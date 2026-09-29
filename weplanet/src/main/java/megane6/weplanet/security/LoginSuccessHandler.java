package megane6.weplanet.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.GroupMemberRepository;
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
	private final GroupMemberRepository groupMemberRepository;

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
		boolean portalLogin = "true".equals(request.getParameter("portalLogin"));
		boolean adminLogin = "true".equals(request.getParameter("adminLogin"));

		// AUTH-11: 관리자 로그인 화면(adminLogin=true)은 ADMIN 계정만 통과시킨다.
		// 예전에는 이 파라미터가 있으면 역할 검사를 아예 건너뛰어서, 아티스트 그룹 계정이 관리자 로그인 화면으로
		// 들어오면 멤버 프로필 선택 없이 그룹 계정으로 로그인되고 팬/포털 로그인 분리도 우회됐다.
		// 역할 정보는 노출하지 않고, 일반 로그인 실패와 같은 화면으로 보낸다.
		if (adminLogin) {
			if (!"ROLE_ADMIN".equals(principal.getRoleName())) {
				clearAuthentication(request);
				getRedirectStrategy().sendRedirect(request, response, "/admin/login?error");
				return;
			}
		} else if (portalLogin) {
			// 포털 로그인: 아티스트/에이전시 전용. 선택 탭과 실제 역할이 일치해야 함.
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
		} else {
			// 일반 팬 로그인(/login, /login/id): 아티스트·에이전시 계정 차단
			// 역할 정보는 노출하지 않고, 일반 로그인 실패와 동일한 화면으로 보낸다.
			String roleName = principal.getRoleName();
			if ("ROLE_ARTIST".equals(roleName) || "ROLE_AGENCY".equals(roleName)
					|| "ROLE_ARTIST_MEMBER".equals(roleName)) {
				clearAuthentication(request);
				getRedirectStrategy().sendRedirect(request, response, "/login/id?error");
				return;
			}
		}
		
		// 멤버가 있는 그룹 계정: 여기서는 로그인시키지 않고 프로필 선택(2단계)으로 보낸다.
		// clearAuthentication 이 세션을 통째로 버리고 새 세션을 만들기 때문에,
		// 방금 저장된 그룹 로그인 정보는 사라지고 "대기 그룹 id" 만 새 세션에 남는다.
		// AUTH-11: 로그인 경로(portalLogin 여부)와 관계없이 항상 적용 - 어떤 화면으로 들어오든 그룹 비밀번호만으로는
		// 활동할 수 없고 반드시 멤버 프로필을 고르게 한다.
		if ("ROLE_ARTIST".equals(principal.getRoleName())
				&& groupMemberRepository.existsByGroupIdAndLeftAtIsNull(principal.getId())) {
			clearAuthentication(request);
			ArtistProfileLoginSupport.begin(request.getSession(true), principal.getId());
			getRedirectStrategy().sendRedirect(request, response, "/portal/profiles");
			return;
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
