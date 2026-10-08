package megane6.weplanet.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * 시연용 주소별 첫 화면 - admin.localhost 처럼 역할 이름이 붙은 주소로 홈(/)에 들어오면 그 역할의 로그인 화면으로 보낸다.
 * 로그인하지 않은 경우에만 옮긴다 (로그인한 상태면 평소 홈을 보여 줘서, 로그인 화면과 홈이 서로 돌려보내는 일이 없게).
 * 주소마다 쿠키(세션)가 따로라서, 탭마다 다른 역할로 동시에 로그인할 수 있다.
 */
@Component
public class RoleHostEntryFilter extends OncePerRequestFilter {
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		boolean home = "GET".equalsIgnoreCase(request.getMethod()) && (path.isEmpty() || "/".equals(path));
		if (home && request.getUserPrincipal() == null) {
			String target = entryPageFor(request.getServerName());
			if (target != null) {
				response.sendRedirect(request.getContextPath() + target);
				return;
			}
		}
		filterChain.doFilter(request, response);
	}
	
	// 주소 맨 앞 이름으로 첫 화면을 고른다. 해당 없으면 null (평소 홈)
	private static String entryPageFor(String host) {
		String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
		if (h.startsWith("admin.")) return "/admin/login";
		if (h.startsWith("artist.")) return "/portal/login?role=ARTIST";
		if (h.startsWith("agency.")) return "/portal/login?role=AGENCY";
		return null;
	}
}
