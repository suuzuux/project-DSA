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
 * 관리자는 관리자 로그인으로, 아티스트/에이전시는 포털 로그인으로, 그 외는 메인홈으로 보낸다.
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
				// 관리자는 일반 회원 로그인 화면이 아니라 관리자 로그인 화면으로 돌아가야 한다.
				if ("ROLE_ADMIN".equals(role)) {
					target = "/admin/login?logout";
					break;
				}
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
