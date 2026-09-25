package megane6.weplanet.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기존 멤버십 가입 POST는 무료 가입이라, 결제 화면으로만 보낸다.
 * CommunityController 는 수정하지 않는다.
 */
@Component
public class MembershipJoinPaymentFilter extends OncePerRequestFilter {

	private static final Pattern JOIN = Pattern.compile("^/community/(\\d+)/membership/join$");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
									FilterChain filterChain) throws ServletException, IOException {
		if ("POST".equalsIgnoreCase(request.getMethod())) {
			Matcher matcher = JOIN.matcher(request.getRequestURI());
			if (matcher.matches()) {
				response.sendRedirect(request.getContextPath()
						+ "/payments/membership/" + matcher.group(1) + "/checkout");
				return;
			}
		}
		filterChain.doFilter(request, response);
	}
}
