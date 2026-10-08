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

/** 역할별 로그아웃 이동 (관리자·포털·메인) 및 화면 언어 유지. */
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
		// 한국어가 아닌 경우에만 새 세션에 언어를 이어 준다.
		if (request.getAttribute(KEPT_LOCALE_ATTR) instanceof Locale kept) {
			PreferredLocaleResolver.restoreLocale(request, kept);
		}
		response.sendRedirect(request.getContextPath() + target);
	}
}
