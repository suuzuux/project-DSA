package megane6.weplanet.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private final UserRepository userRepository;

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
		userRepository.findById(principal.getId())
				.ifPresent(user -> user.recordLogin());

		if ("true".equals(request.getParameter("portalLogin"))) {
			if ("ROLE_ARTIST".equals(principal.getRoleName())) {
				getRedirectStrategy().sendRedirect(request, response, "/portal/dashboard");
				return;
			}
			getRedirectStrategy().sendRedirect(request, response, "/");
			return;
		}

		super.onAuthenticationSuccess(request, response, authentication);
	}
}
