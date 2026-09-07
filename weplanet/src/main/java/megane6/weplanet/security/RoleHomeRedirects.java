package megane6.weplanet.security;

/**
 * 역할별 로그인 후 기본 진입 경로.
 * FAN → 메인 홈, ARTIST → 본인 커뮤니티, AGENCY → 포털, ADMIN → 관리자.
 */
public final class RoleHomeRedirects {

	private RoleHomeRedirects() {
	}

	public static String pathFor(AuthenticatedUser principal) {
		if (principal == null) {
			return "/login";
		}
		String roleName = principal.getRoleName();
		if ("ROLE_ADMIN".equals(roleName)) {
			return "/admin";
		}
		if ("ROLE_AGENCY".equals(roleName)) {
			return "/portal/dashboard";
		}
		if ("ROLE_ARTIST".equals(roleName) && principal.getId() != null) {
			return "/community/" + principal.getId() + "/highlight";
		}
		return "/";
	}

	public static String pathFor(String roleName) {
		if ("ROLE_ADMIN".equals(roleName)) {
			return "/admin";
		}
		if ("ROLE_AGENCY".equals(roleName)) {
			return "/portal/dashboard";
		}
		// ARTIST는 principal id가 필요하므로 pathFor(AuthenticatedUser) 사용
		return "/";
	}

	public static String redirectFor(String roleName) {
		return "redirect:" + pathFor(roleName);
	}

	public static String redirectFor(AuthenticatedUser principal) {
		if (principal == null) {
			return "redirect:/login";
		}
		return "redirect:" + pathFor(principal);
	}
}
