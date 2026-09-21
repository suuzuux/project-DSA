package megane6.weplanet.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 아티스트/에이전시는 포털 로그인으로, 그 외는 메인홈으로 보낸다.
 */
@Component
public class RoleAwareLogoutSuccessHandler implements LogoutSuccessHandler {

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
								Authentication authentication) throws IOException, ServletException {
		String target = "/";
		if (authentication != null) {
			for (GrantedAuthority authority : authentication.getAuthorities()) {
				String role = authority.getAuthority();
				if ("ROLE_ARTIST".equals(role)) {
					target = "/portal/login?role=ARTIST";
					break;
				}
				if ("ROLE_AGENCY".equals(role)) {
					target = "/portal/login?role=AGENCY";
					break;
				}
			}
		}
		response.sendRedirect(request.getContextPath() + target);
	}
}
