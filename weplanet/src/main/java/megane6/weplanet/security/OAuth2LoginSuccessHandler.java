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
import megane6.weplanet.domain.entity.enumfolder.SocialLoginIntent;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.email.MarketingConsentEmailService;
import megane6.weplanet.util.NicknameGenerator;
import megane6.weplanet.util.UsernameGenerator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final UsernameGenerator usernameGenerator;
	private final NicknameGenerator nicknameGenerator;
	private final SocialLoginSessionSupport socialLoginSessionSupport;
	private final MarketingConsentEmailService marketingConsentEmailService;
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
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login?error");
				return;
			}
			if (user.getStatus() == UserStatus.DORMANT) {
				// 소셜 인증은 됐지만, 로컬 로그인과 동일하게 이메일 코드 인증을 한 번 더 거치게 한다.
				// AUTH-10: 로컬/소셜 진입 구분 없이 항상 같은 화면(아이디 입력 → 인증코드)으로 통일했으므로,
				// 여기서 더 이상 세션에 대상 유저를 미리 심어두지 않는다 - DormantAccountReactivationController 참고.
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login/reactivate");
				return;
			}
		} else {
			if (intent != SocialLoginIntent.SIGNUP) {
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login?socialNotFound=true");
				return;
			}

			Optional<User> sameEmailUser = userRepository.findByEmail(profile.email());
			if (sameEmailUser.isPresent()) {
				// AUTH-10: 이메일이 겹치면 더 이상 자동으로 "연동하시겠습니까?" 확인 화면을 띄우지 않는다.
				// 이미 가입된 이메일이라는 것만 안내하고, 연동 자체는 로그인 후 설정 화면에서 능동적으로 하게 한다.
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/signup?socialEmailTaken=true");
				return;
			}

			user = createNewSocialUser(provider, profile);
		}

		if (session != null) {
			session.removeAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT);
		}

		user.recordLogin();
		socialLoginSessionSupport.loginAs(user, request, response);
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

	// AUTH-10: 신규 소셜 가입은 비밀번호를 만들지 않는다(null). 가입 직후 실명/이메일을 따로 입력받던 절차도
	// 없앴으므로 프로필이 이 시점에 바로 확정된다 - 필요하면 나중에 설정 화면에서 이름/이메일/비밀번호를 고치면 된다.
	private User createNewSocialUser(AuthProvider provider, SocialProfile profile) {
		String username = usernameGenerator.generate(provider);
		String nickname = resolveNickname(profile.suggestedNickname());

		User newUser = User.createSocialFan(username, null, profile.realName(), nickname, profile.email(), provider, profile.providerId());
		User saved = userRepository.save(newUser);
		
		// [광고성 정보 알림] 데모용 - 소셜 계정은 "(선택) 광고 및 마케팅 활용 동의" 체크박스 자체가 없어서
		// 항상 marketingConsentGiven=false로 보낸다. 가입 완료 메일은 아이디/비밀번호 가입과 동일하게
		// 동의 여부와 무관하게 무조건 1통 보낸다 (UserService.signup() 참고).
		try {
			marketingConsentEmailService.sendSignupWelcomeEmail(saved, false);
		} catch (Exception e) {
			log.error("[광고성 정보 알림] 소셜 회원가입 환영 메일 발송 실패: user={}", saved.getId(), e);
		}
		
		return saved;
	}
	
	private String resolveNickname(String suggestedNickname) {
		if (suggestedNickname != null && !suggestedNickname.isBlank() && !userRepository.existsByNickname(suggestedNickname)) {
			return suggestedNickname;
		}
		return nicknameGenerator.generate();
	}
}
