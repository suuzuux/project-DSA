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

	// provider별로 다른 응답 구조(구글 OIDC / 카카오 REST API)에서 뽑아낸 값들을 한데 모은 것.
	// email/realName은 카카오처럼 실제로 못 받는 provider의 경우 placeholder가 들어갈 수 있다.
	// suggestedNickname은 "이 값을 그대로 닉네임으로 써도 되는지"(중복 여부)는 아직 확인 전인 원본 후보값 -
	// 신규 계정을 실제로 만들 때(resolveNickname)만 UNIQUE 충돌 검사를 한다.
	private record SocialProfile(String providerId, String email, String realName, String suggestedNickname) {}

	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
										Authentication authentication) throws IOException, ServletException {

		OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
		OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
		// registrationId = application.properties에 등록한 provider 키값("google"/"kakao") -> AuthProvider enum과 이름이 같다.
		AuthProvider provider = AuthProvider.valueOf(oauthToken.getAuthorizedClientRegistrationId().toUpperCase());

		SocialProfile profile = extractProfile(provider, oAuth2User);

		HttpSession session = request.getSession(false);
		SocialLoginIntent intent = session != null
				? (SocialLoginIntent) session.getAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT)
				: null;

		Optional<User> existingUser = userRepository.findByProviderAndProviderId(provider, profile.providerId());

		User user;
		// 방금 이 요청 안에서 새로 만든 계정인지 여부. true이고 provider가 email/realName을 placeholder로
		// 채우는 provider(카카오/LINE)라면, 로그인 처리 후 곧장 홈으로 보내지 않고 실명/이메일을
		// 한 번만 입력받는 화면(/social-login/complete-profile)으로 보낸다 (아래 세션 플래그 참고).
		boolean newlyCreated = false;
		if (existingUser.isPresent()) {
			if (intent == SocialLoginIntent.SIGNUP) {
				// 회원가입 화면에서 "OO로 가입하기"를 눌렀는데, 이 계정으로는 이미 가입이 끝나 있는 경우.
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
				// 자체는 이미 성공 처리해서 세션에 소셜 원본 principal(DefaultOidcUser 등)을 저장해둔 상태라
				// 이후 요청(index.html의 #authentication.principal.nickname 등)에서 그 원본 principal에
				// nickname 필드가 없어 500 에러가 난다. 우리 서비스 계정으로 로그인시키지 않을 거면
				// SecurityContext도 같이 비워서 실제로 로그인 안 된 상태로 되돌려야 한다.
				socialLoginSessionSupport.clearSecurityContext(request, response);
				response.sendRedirect("/login?socialNotFound=true");
				return;
			}

			// 회원가입 화면에서 소셜 가입을 시도한 경우, 새 계정을 만들기 전에
			// 같은 이메일의 기존 계정(주로 아이디/비밀번호로 가입한 LOCAL 계정)이 있는지 먼저 확인한다.
			// (카카오처럼 실제 이메일이 없는 provider는 항상 이 provider 전용 placeholder 이메일이라
			// 기존 계정과 절대 겹치지 않으므로, 이 분기가 사실상 항상 통과되어 바로 새 계정 생성으로 이어진다.)
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
			if (newlyCreated && provider.usesPlaceholderProfile()) {
				session.setAttribute(SocialLoginEntryController.SESSION_KEY_PROFILE_COMPLETION_REQUIRED, Boolean.TRUE);
			}
		}

		user.recordLogin();
		socialLoginSessionSupport.loginAs(user, request, response);

		// 가입 직후 한 번만: 방금 만든 계정이고 실명/이메일이 placeholder라면, 홈 대신 입력 화면으로 보낸다.
		// 사용자가 그 화면에서 실제로 입력을 완료하면(SocialLoginEntryController) 이 세션 플래그가 지워지므로
		// 재로그인 시에는 다시 뜨지 않는다.
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
		// GOOGLE (OIDC). sub = 구글 계정 고유 ID, email/name = 구글이 제공하는 이메일/이름.
		// 구글은 기존 동작 그대로 닉네임은 항상 랜덤 생성한다(suggestedNickname=null) - 구글 name은
		// 닉네임이 아니라 realName(결제 명의 대조용)으로 쓰인다.
		String providerId = oAuth2User.getAttribute("sub");
		String email = oAuth2User.getAttribute("email");
		String name = oAuth2User.getAttribute("name");
		return new SocialProfile(providerId, email, name, null);
	}

	@SuppressWarnings("unchecked")
	private SocialProfile extractKakaoProfile(OAuth2User oAuth2User) {
		// 카카오 응답은 id가 최상위(숫자), 닉네임은 kakao_account.profile.nickname처럼 중첩되어 있다.
		// 이메일/실명 동의항목은 신청하지 않았으므로 카카오 계정에서 절대 내려오지 않는다.
		// 주의: getAttribute(...)의 결과를 String.valueOf(...)에 바로 넘기면, 제네릭 타입 추론 때문에
		// 컴파일러가 String.valueOf(char[]) 오버로드로 잘못 바인딩해서 (Long -> char[] 캐스팅 실패로) 런타임에
		// ClassCastException이 난다. Object 변수에 먼저 담아서 오버로드 모호성을 없앤다.
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

		// email 컬럼이 NOT NULL + UNIQUE라서 값이 꼭 있어야 한다. providerId 기반이라 항상 유일하다.
		String email = "kakao_" + providerId + "@kakao.weplanet.local";
		// 카카오는 실명(name) 동의항목이 개인 개발자에게 아예 제공되지 않아 실제 실명을 받을 방법이 없다.
		// 임시로 닉네임(없으면 고정 문구)을 realName 자리에 채워둔다 - 결제 명의 대조가 필요해지면
		// 별도 본인인증으로 보완해야 함 (emailVerifiedAt 미처리와 비슷한 성격의 알려진 한계, 담당자 확인 필요).
		String realName = (kakaoNickname != null && !kakaoNickname.isBlank()) ? kakaoNickname : "카카오사용자";

		return new SocialProfile(providerId, email, realName, kakaoNickname);
	}

	private User createNewSocialUser(AuthProvider provider, SocialProfile profile) {
		String username = usernameGenerator.generate(provider);
		String nickname = resolveNickname(profile.suggestedNickname());
		String encodedPassword = passwordEncoder.encode(UUID.randomUUID().toString());

		User newUser = User.createSocialFan(username, encodedPassword, profile.realName(), nickname, profile.email(), provider, profile.providerId());
		return userRepository.save(newUser);
	}

	// 카카오 닉네임처럼 provider가 제안한 닉네임이 있으면 그대로 쓰되, 비어있거나 이미 다른 회원이
	// 쓰고 있으면(닉네임 UNIQUE 제약) 랜덤 생성으로 대체한다. 제안값이 아예 없으면(구글) 바로 랜덤 생성.
	private String resolveNickname(String suggestedNickname) {
		if (suggestedNickname != null && !suggestedNickname.isBlank() && !userRepository.existsByNickname(suggestedNickname)) {
			return suggestedNickname;
		}
		return nicknameGenerator.generate();
	}
}
