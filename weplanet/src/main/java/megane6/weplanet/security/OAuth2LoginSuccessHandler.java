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

		// AUTH-10 신설: 로그인된 계정에 소셜을 "연결하기"로 추가하는 흐름은 로그인/가입과 완전히 다르게 처리한다.
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
				// AUTH-11: 예전엔 /login?error 로만 보내서 로그인 화면에 아무 안내도 뜨지 않았다
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login?accountUnavailable=true");
				return;
			}
			// 소셜 로그인은 팬 계정만 - 아이디 로그인이 팬/포털/관리자 화면을 역할별로 나누는 것(LoginSuccessHandler)과 같은 규칙.
			// 예전에는 관리자·아티스트·소속사 계정도 설정 화면에서 소셜을 연결하면 팬 소셜 로그인으로 바로 들어와서,
			// 관리자 로그인 화면이나 그룹 멤버 프로필 선택을 거치지 않았다. (연결 자체도 이제 팬만 할 수 있다 - SocialLoginEntryController.startLink)
			if (user.getRole() != Role.FAN) {
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login?socialFanOnly=true");
				return;
			}
			if (user.getStatus() == UserStatus.DORMANT && user.hasPlaceholderEmail()) {
				// AUTH-11: 카카오/LINE 가입자는 이메일이 받을 수 없는 시스템 주소(*.weplanet.local)라서 이메일 인증코드
				// 방식으로는 휴면을 풀 수 없었다(아이디도 자동 생성이라 본인이 모름). 방금 소셜 인증을 통과한 것 자체가
				// 본인 확인이므로, 이 경우는 코드 없이 바로 휴면을 해제하고 로그인시킨다.
				user.reactivate();
				log.info("[휴면계정] 소셜 재로그인으로 휴면 해제: userId={}, provider={}", user.getId(), provider);
			} else if (user.getStatus() == UserStatus.DORMANT) {
				// 소셜 인증은 됐지만, 로컬 로그인과 동일하게 이메일 코드 인증을 한 번 더 거치게 한다.
				// AUTH-10: 로컬/소셜 진입 구분 없이 항상 같은 화면(아이디 입력 → 인증코드)으로 통일했으므로,
				// 여기서 더 이상 세션에 대상 유저를 미리 심어두지 않는다 - DormantAccountReactivationController 참고.
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login/reactivate");
				return;
			}
		} else {
			// 가입된 계정이 없는 소셜 계정. 예전에는 로그인 화면에서 온 경우 "가입된 계정이 없습니다" 안내만 띄우고,
			// 회원가입 화면에서 온 경우 바로 계정을 만들었다. 이제 어느 쪽에서 왔든 "이 계정으로 가입하시겠습니까?"
			// 확인 화면(약관 동의 포함)으로 보내고, [예]를 누르면 그때 계정을 만든다 (SocialLoginEntryController).
			// 소셜 인증은 이미 끝났으므로 받은 소셜 정보를 세션에 잠깐 담아두고, 구글/카카오 화면을 다시 거치지 않는다.
			socialLoginSessionSupport.clearSecurityContext(request, response);
			boolean fromSignup = intent == SocialLoginIntent.SIGNUP;
			if (userRepository.existsByEmail(profile.email())) {
				// AUTH-10: 이메일이 겹치면 자동으로 연동하지 않는다. 이미 가입된 이메일이라는 것만 안내하고,
				// 연동 자체는 로그인 후 설정 화면에서 능동적으로 하게 한다.
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
		// SETTINGS-03 로케일 버그#2 수정: 소셜 로그인도 로컬 로그인과 동일하게, 세션 로케일을
		// DB에 저장된 선호 언어로 맞춰준 뒤 리다이렉트한다.
		localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(user.getPreferredLanguage()));
		response.sendRedirect("/");
	}

	// 로그인 중인 계정에 소셜을 연동하는 흐름 (설정 화면 "연결하기"). 신규 계정을 만들거나 로그인 상태를
	// 바꾸지 않고, 이미 로그인돼 있던 그 계정(targetUser)에 provider/providerId만 옮겨 붙인다.
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

		// 이 소셜 계정(provider+providerId)이 이미 "다른" 계정에 연동돼 있으면 차단한다
		// (users 테이블의 (provider, provider_id) 조합이 계정당 유일해야 하는 것과도 맞물림).
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
			// 계정당 소셜 연동은 최대 1개 - 이미 다른 걸 연동한 상태라면 "바꾸시겠습니까?" 확인부터 받는다.
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
