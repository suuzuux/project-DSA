package megane6.weplanet.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/**
 * 관리자는 관리자 로그인으로, 아티스트/에이전시는 포털 로그인으로, 그 외는 메인홈으로 보낸다.
 *
 * 화면 언어 유지: 로그아웃은 세션을 통째로 버리므로, LogoutHandler 로도 등록해 세션을 버리기 전에 언어를 읽어 두고(logout)
 * 로그아웃이 끝난 뒤 새 세션에 다시 넣는다(onLogoutSuccess). 관리자는 한국어 고정이라 따로 하지 않는다.
 */
@Component
public class RoleAwareLogoutSuccessHandler implements LogoutHandler, LogoutSuccessHandler {

	private static final String KEPT_LOCALE_ATTR = RoleAwareLogoutSuccessHandler.class.getName() + ".locale";

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		Locale locale = PreferredLocaleResolver.storedLocale(request);
		if (locale != null && !Locale.KOREAN.equals(locale) && !PreferredLocaleResolver.isAdmin(authentication)) {
			request.setAttribute(KEPT_LOCALE_ATTR, locale);
		}
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
								Authentication authentication) throws IOException, ServletException {
		String target = "/";
		if (authentication != null) {
			for (GrantedAuthority authority : authentication.getAuthorities()) {
				String role = authority.getAuthority();
				// 관리자는 일반 회원 로그인 화면이 아니라 관리자 로그인 화면으로 돌아가야 한다.
				if ("ROLE_ADMIN".equals(role)) {
					target = "/admin/login?logout";
					break;
				}
				if ("ROLE_ARTIST".equals(role) || "ROLE_ARTIST_MEMBER".equals(role)) {
					target = "/portal/login?role=ARTIST";
					break;
				}
				if ("ROLE_AGENCY".equals(role)) {
					target = "/portal/login?role=AGENCY";
					break;
				}
			}
		}
		// 한국어가 아닌 언어를 쓰던 경우에만 새 세션을 열어 언어를 이어 준다 (한국어는 기본값이라 세션이 필요 없다)
		if (request.getAttribute(KEPT_LOCALE_ATTR) instanceof Locale kept) {
			PreferredLocaleResolver.restoreLocale(request, kept);
		}
		response.sendRedirect(request.getContextPath() + target);
	}
}
