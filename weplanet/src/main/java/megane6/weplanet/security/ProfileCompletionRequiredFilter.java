package megane6.weplanet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.controller.SocialLoginEntryController;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class ProfileCompletionRequiredFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_PREFIXES = List.of(
            "/css/",
            "/js/",
            "/img/"
    );

    private static final List<String> ALLOWED_EXACT_PATHS = List.of(
            "/social-login/complete-profile",
            "/social-login/complete-profile/cancel",
            "/settings/email/code",
            "/settings/email/verify",
            "/logout",
            "/error"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        boolean completionRequired = session != null
                && Boolean.TRUE.equals(session.getAttribute(SocialLoginEntryController.SESSION_KEY_PROFILE_COMPLETION_REQUIRED));

        if (!completionRequired || isAllowed(request.getRequestURI(), request.getContextPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/social-login/complete-profile");
    }

    private boolean isAllowed(String requestUri, String contextPath) {
        String path = (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath))
                ? requestUri.substring(contextPath.length())
                : requestUri;

        if (ALLOWED_EXACT_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
