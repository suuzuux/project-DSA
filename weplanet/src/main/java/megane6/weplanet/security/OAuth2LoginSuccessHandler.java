package megane6.weplanet.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.controller.SocialLoginEntryController;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.SocialLoginIntent;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final SocialLoginSessionSupport socialLoginSessionSupport;
	private final LocaleResolver localeResolver;
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

		// 설정 화면 소셜 연결은 따로 처리한다.
		if (intent == SocialLoginIntent.LINK) {
			handleLink(provider, profile, session, request, response);
			return;
		}

		Optional<User> existingUser = userRepository.findByProviderAndProviderId(provider, profile.providerId());

		User user;

		if (existingUser.isPresent()) {
			if (intent == SocialLoginIntent.SIGNUP) {
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/signup?socialAlreadyRegistered=true");
				return;
			}
			user = existingUser.get();

			if (user.getStatus() == UserStatus.WITHDRAWN || user.getStatus() == UserStatus.SUSPENDED) {
				// 로그인 화면에 "이용할 수 없는 계정" 안내를 띄운다
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login?accountUnavailable=true");
				return;
			}
			// 소셜 로그인은 팬 계정만 가능하다.
			if (user.getRole() != Role.FAN) {
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login?socialFanOnly=true");
				return;
			}
			if (user.getStatus() == UserStatus.DORMANT && user.hasPlaceholderEmail()) {
				// 카카오·LINE 가입자는 소셜 인증을 본인 확인으로 보고 휴면을 바로 해제한다.
				user.reactivate();
				log.info("[휴면계정] 소셜 재로그인으로 휴면 해제: userId={}, provider={}", user.getId(), provider);
			} else if (user.getStatus() == UserStatus.DORMANT) {
				// 그 밖의 휴면 계정은 휴면 해제 화면으로 보낸다.
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login/reactivate");
				return;
			}
		} else {
			// 계정이 없는 소셜 계정은 가입 확인 화면으로 보낸다 (소셜 정보는 세션에 보관).
			socialLoginSessionSupport.clearSecurityContext(request, response);
			boolean fromSignup = intent == SocialLoginIntent.SIGNUP;
			if (userRepository.existsByEmail(profile.email())) {
				// 이메일이 겹치면 자동 연동하지 않고 안내만 한다.
				response.sendRedirect(fromSignup ? "/signup?socialEmailTaken=true" : "/login?socialEmailTaken=true");
				return;
			}
			HttpSession pendingSession = request.getSession(true);
			pendingSession.removeAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT);
			pendingSession.setAttribute(SocialLoginEntryController.SESSION_KEY_PENDING_SIGNUP,
					PendingSocialSignup.of(provider, profile.providerId(), profile.email(),
							profile.realName(), profile.suggestedNickname()));
			response.sendRedirect("/social-login/signup-confirm");
			return;
		}

		if (session != null) {
			session.removeAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT);
		}

		user.recordLogin();
		socialLoginSessionSupport.loginAs(user, request, response);
		// 계정 선호 언어로 화면 언어를 맞춘다.
		localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(user.getPreferredLanguage()));
		response.sendRedirect("/");
	}

	// 로그인 중인 계정에 소셜을 연동한다 (새 계정·로그인 상태 변경 없음).
	private void handleLink(AuthProvider provider, SocialProfile profile, HttpSession session,
							HttpServletRequest request, HttpServletResponse response) throws IOException {
		Long targetUserId = session != null
				? (Long) session.getAttribute(SocialLoginEntryController.SESSION_KEY_LINK_TARGET_USER_ID)
				: null;
		if (session != null) {
			session.removeAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT);
			session.removeAttribute(SocialLoginEntryController.SESSION_KEY_LINK_TARGET_USER_ID);
		}
		if (targetUserId == null) {
			socialLoginSessionSupport.clearSecurityContext(request, response);
			response.sendRedirect("/login");
			return;
		}
		Optional<User> targetOpt = userRepository.findById(targetUserId);
		if (targetOpt.isEmpty()) {
			socialLoginSessionSupport.clearSecurityContext(request, response);
			response.sendRedirect("/login");
			return;
		}
		User targetUser = targetOpt.get();

		// 이 소셜 계정이 다른 계정에 연동돼 있으면 막는다.
		Optional<User> owner = userRepository.findByProviderAndProviderId(provider, profile.providerId());
		if (owner.isPresent() && !owner.get().getId().equals(targetUser.getId())) {
			socialLoginSessionSupport.loginAs(targetUser, request, response);
			response.sendRedirect("/settings?linkError=alreadyLinkedElsewhere");
			return;
		}

		boolean alreadyThisIdentity = provider.equals(targetUser.getProvider())
				&& profile.providerId().equals(targetUser.getProviderId());
		if (alreadyThisIdentity) {
			socialLoginSessionSupport.loginAs(targetUser, request, response);
			response.sendRedirect("/settings?linkNotice=alreadyLinked");
			return;
		}

		if (targetUser.getProvider() != null) {
			// 소셜 연동은 계정당 1개 (이미 있으면 교체 확인).
			if (session != null) {
				session.setAttribute(SocialLoginEntryController.SESSION_KEY_PENDING_LINK,
						new PendingSocialLink(provider, profile.providerId(), targetUser.getId()));
			}
			socialLoginSessionSupport.loginAs(targetUser, request, response);
			response.sendRedirect("/social-login/link-confirm");
			return;
		}

		targetUser.linkSocialProvider(provider, profile.providerId());
		socialLoginSessionSupport.loginAs(targetUser, request, response);
		response.sendRedirect("/settings?linked=true");
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

}
