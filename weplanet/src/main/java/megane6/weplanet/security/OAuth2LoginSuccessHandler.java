package megane6.weplanet.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.SocialLoginEntryController;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.SocialLoginIntent;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.util.NicknameGenerator;
import megane6.weplanet.util.UsernameGenerator;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final UsernameGenerator usernameGenerator;
	private final NicknameGenerator nicknameGenerator;
	private final BCryptPasswordEncoder passwordEncoder;
	private final SocialLoginSessionSupport socialLoginSessionSupport;
	private record SocialProfile(String providerId, String email, String realName, String suggestedNickname) {}

	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
										Authentication authentication) throws IOException, ServletException {

		OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
		OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
		AuthProvider provider = AuthProvider.valueOf(oauthToken.getAuthorizedClientRegistrationId().toUpperCase());

		SocialProfile profile = extractProfile(provider, oAuth2User);

		HttpSession session = request.getSession(false);
		SocialLoginIntent intent = session != null
				? (SocialLoginIntent) session.getAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT)
				: null;

		Optional<User> existingUser = userRepository.findByProviderAndProviderId(provider, profile.providerId());

		User user;
		
		boolean newlyCreated = false;
		if (existingUser.isPresent()) {
			if (intent == SocialLoginIntent.SIGNUP) {
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/signup?socialAlreadyRegistered=true");
				return;
			}
			user = existingUser.get();
		} else {
			if (intent != SocialLoginIntent.SIGNUP) {
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login?socialNotFound=true");
				return;
			}

			
			Optional<User> sameEmailUser = userRepository.findByEmail(profile.email());
			if (sameEmailUser.isPresent()) {
				socialLoginSessionSupport.clearSecurityContext(request, response);
				if (session != null) {
					session.setAttribute(
							SocialLoginEntryController.SESSION_KEY_PENDING_SOCIAL_SIGNUP,
							new PendingSocialSignup(provider, profile.providerId(), profile.email(), profile.realName()));
				}
				response.sendRedirect("/social-login/email-conflict");
				return;
			}

			user = createNewSocialUser(provider, profile);
			newlyCreated = true;
		}

		if (session != null) {
			session.removeAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT);
			if (newlyCreated && provider.requiresProfileCompletion()) {
				session.setAttribute(SocialLoginEntryController.SESSION_KEY_PROFILE_COMPLETION_REQUIRED, Boolean.TRUE);
			}
		}

		user.recordLogin();
		socialLoginSessionSupport.loginAs(user, request, response);


		if (session != null && Boolean.TRUE.equals(session.getAttribute(SocialLoginEntryController.SESSION_KEY_PROFILE_COMPLETION_REQUIRED))) {
			response.sendRedirect("/social-login/complete-profile");
			return;
		}
		response.sendRedirect("/");
	}

	private SocialProfile extractProfile(AuthProvider provider, OAuth2User oAuth2User) {
		if (provider == AuthProvider.KAKAO) {
			return extractKakaoProfile(oAuth2User);
		}
		if (provider == AuthProvider.LINE) {
			return extractLineProfile(oAuth2User);
		}
		String providerId = oAuth2User.getAttribute("sub");
		String email = oAuth2User.getAttribute("email");
		String name = oAuth2User.getAttribute("name");
		return new SocialProfile(providerId, email, name, name);
	}

	@SuppressWarnings("unchecked")
	private SocialProfile extractKakaoProfile(OAuth2User oAuth2User) {
		Object idAttribute = oAuth2User.getAttribute("id");
		String providerId = String.valueOf(idAttribute);

		String kakaoNickname = null;
		Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
		if (kakaoAccount != null) {
			Map<String, Object> kakaoProfile = (Map<String, Object>) kakaoAccount.get("profile");
			if (kakaoProfile != null) {
				kakaoNickname = (String) kakaoProfile.get("nickname");
			}
		}
		
		String email = "kakao_" + providerId + "@kakao.weplanet.local";
		
		String realName = (kakaoNickname != null && !kakaoNickname.isBlank()) ? kakaoNickname : AuthProvider.KAKAO.placeholderRealName();

		return new SocialProfile(providerId, email, realName, kakaoNickname);
	}

	private SocialProfile extractLineProfile(OAuth2User oAuth2User) {
		String providerId = oAuth2User.getAttribute("userId");
		String displayName = oAuth2User.getAttribute("displayName");
		String email = "line_" + providerId + "@line.weplanet.local";
		String realName = (displayName != null && !displayName.isBlank()) ? displayName : AuthProvider.LINE.placeholderRealName();

		return new SocialProfile(providerId, email, realName, displayName);
	}

	private User createNewSocialUser(AuthProvider provider, SocialProfile profile) {
		String username = usernameGenerator.generate(provider);
		String nickname = resolveNickname(profile.suggestedNickname());
		String encodedPassword = passwordEncoder.encode(UUID.randomUUID().toString());

		User newUser = User.createSocialFan(username, encodedPassword, profile.realName(), nickname, profile.email(), provider, profile.providerId());
		return userRepository.save(newUser);
	}
	
	private String resolveNickname(String suggestedNickname) {
		if (suggestedNickname != null && !suggestedNickname.isBlank() && !userRepository.existsByNickname(suggestedNickname)) {
			return suggestedNickname;
		}
		return nicknameGenerator.generate();
	}
}
