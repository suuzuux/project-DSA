package megane6.weplanet.security;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.servlet.LocaleResolver;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 소셜 로그인은 팬 계정만 - 관리자·아티스트·소속사 계정에 연결된 소셜로는 들어올 수 없다
class OAuth2LoginSuccessHandlerTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final SocialLoginSessionSupport sessionSupport = mock(SocialLoginSessionSupport.class);
	private final OAuth2LoginSuccessHandler handler =
			new OAuth2LoginSuccessHandler(userRepository, sessionSupport, mock(LocaleResolver.class));

	@Test
	void adminLinkedToGoogleCannotLogInWithGoogle() throws Exception {
		User admin = User.createAdmin("admin01", "encoded", "관리자", "관리자", "admin01@test.com");
		admin.linkSocialProvider(AuthProvider.GOOGLE, "google-sub-1");
		when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-1")).thenReturn(Optional.of(admin));

		MockHttpServletResponse response = new MockHttpServletResponse();
		handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, googleLogin("google-sub-1"));

		assertEquals("/login?socialFanOnly=true", response.getRedirectedUrl());
		verify(sessionSupport, never()).loginAs(any(), any(), any());
	}

	@Test
	void fanLogsInWithGoogle() throws Exception {
		User fan = User.createSocialFan("google123456", null, "홍길동", "닉네임", "hong@gmail.com",
				AuthProvider.GOOGLE, "google-sub-2");
		when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-2")).thenReturn(Optional.of(fan));

		MockHttpServletResponse response = new MockHttpServletResponse();
		handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, googleLogin("google-sub-2"));

		assertEquals("/", response.getRedirectedUrl());
		verify(sessionSupport).loginAs(eq(fan), any(), any());
	}

	private static OAuth2AuthenticationToken googleLogin(String sub) {
		DefaultOAuth2User principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
				Map.of("sub", sub, "email", "someone@gmail.com", "name", "Someone"), "sub");
		return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
	}
}
