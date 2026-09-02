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
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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

	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
										Authentication authentication) throws IOException, ServletException {

		OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
		// Phase 1: 구글(OIDC)만 지원. sub = 구글 계정 고유 ID, email/name = 구글이 제공하는 이메일/이름
		String providerId = oAuth2User.getAttribute("sub");
		String email = oAuth2User.getAttribute("email");
		String name = oAuth2User.getAttribute("name");

		HttpSession session = request.getSession(false);
		SocialLoginIntent intent = session != null
				? (SocialLoginIntent) session.getAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT)
				: null;

		Optional<User> existingUser = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId);

		User user;
		if (existingUser.isPresent()) {
			if (intent == SocialLoginIntent.SIGNUP) {
				// 회원가입 화면에서 "구글로 가입하기"를 눌렀는데, 이 구글 계정으로는 이미 가입이 끝나 있는 경우.
				// 그냥 로그인시켜버리면 "가입하기"를 눌렀는데 조용히 로그인만 되는 꼴이라 사용자가 혼란스러우니,
				// 로그인시키지 않고 회원가입 화면으로 돌려보내면서 이미 가입된 계정이라고 안내한다.
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/signup?socialAlreadyRegistered=true");
				return;
			}
			user = existingUser.get();
		} else {
			if (intent != SocialLoginIntent.SIGNUP) {
				// 로그인 페이지에서 눌렀는데 가입된 계정이 없는 경우 -> 자동 생성하지 않고 안내 화면으로.
				// 주의: 여기서 SecurityContext를 비워주지 않으면, Spring Security가 OAuth2 인증
				// 자체는 이미 성공 처리해서 세션에 구글 원본 principal(DefaultOidcUser)을 저장해둔 상태라
				// 이후 요청(index.html의 #authentication.principal.nickname 등)에서 그 원본 principal에
				// nickname 필드가 없어 500 에러가 난다. 우리 서비스 계정으로 로그인시키지 않을 거면
				// SecurityContext도 같이 비워서 실제로 로그인 안 된 상태로 되돌려야 한다.
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login?socialNotFound=true");
				return;
			}

			// 회원가입 화면에서 구글로 가입 시도한 경우, 새 계정을 만들기 전에
			// 같은 이메일의 기존 계정(주로 아이디/비밀번호로 가입한 LOCAL 계정)이 있는지 먼저 확인한다.
			// 구글이 이메일 소유는 이미 검증해준 상태지만, "두 계정을 합칠지"는 사용자 확인을 받아야 하므로
			// 여기서 바로 합치지 않고, 확인 화면으로 보내고 리턴한다.
			Optional<User> sameEmailUser = userRepository.findByEmail(email);
			if (sameEmailUser.isPresent()) {
				socialLoginSessionSupport.clearSecurityContext(request, response);
				if (session != null) {
					session.setAttribute(
							SocialLoginEntryController.SESSION_KEY_PENDING_SOCIAL_SIGNUP,
							new PendingSocialSignup(AuthProvider.GOOGLE, providerId, email, name));
				}
				response.sendRedirect("/social-login/email-conflict");
				return;
			}

			user = createNewSocialUser(email, name, providerId);
		}

		if (session != null) {
			session.removeAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT);
		}

		user.recordLogin();
		socialLoginSessionSupport.loginAs(user, request, response);

		response.sendRedirect("/");
	}

	private User createNewSocialUser(String email, String name, String providerId) {
		String username = usernameGenerator.generate(AuthProvider.GOOGLE);
		String nickname = nicknameGenerator.generate();
		String encodedPassword = passwordEncoder.encode(UUID.randomUUID().toString());

		User newUser = User.createSocialFan(username, encodedPassword, name, nickname, email, AuthProvider.GOOGLE, providerId);
		return userRepository.save(newUser);
	}
}
