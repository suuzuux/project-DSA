package megane6.weplanet.security;

import megane6.weplanet.repository.GroupMemberRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.portal.AgencyEnrollmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.LocaleResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class LoginSuccessHandlerTest {

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void directPasswordLoginCannotBypassAdminEmailVerification() throws Exception {
		LoginSuccessHandler handler = new LoginSuccessHandler(
				mock(UserRepository.class),
				mock(AgencyEnrollmentService.class),
				mock(LocaleResolver.class),
				mock(GroupMemberRepository.class),
				mock(LoginAttemptService.class)
		);
		AuthenticatedUser principal = AuthenticatedUser.builder()
				.id(1L)
				.username("admin")
				.password("encoded")
				.nickname("관리자")
				.roleName("ROLE_ADMIN")
				.enabled(true)
				.build();
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
				principal,
				null,
				principal.getAuthorities()
		);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
		request.addParameter("adminLogin", "true");
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(request, response, authentication);

		assertEquals("/admin/login?error", response.getRedirectedUrl());
		assertNull(SecurityContextHolder.getContext().getAuthentication());
	}
}
