package megane6.weplanet.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import megane6.weplanet.controller.SocialLoginEntryController;
import megane6.weplanet.domain.entity.enumfolder.SocialLoginIntent;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

// [카카오 계정 전환 문제 해결 - 2026-09-03 회원가입 전용으로 범위 축소]
// 카카오는 구글과 달리 브라우저에 카카오 로그인 세션(쿠키)이 남아있으면 "카카오로 로그인/가입하기" 버튼을
// 눌러도 로그인 화면을 아예 보여주지 않고 그 세션의 계정으로 곧장 인가를 완료해버린다.
//
// 처음엔 로그인/가입 모두에 prompt=login(카카오가 지원하는, 세션이 있어도 로그인 화면을 강제로 다시
// 보여주는 파라미터)을 걸었는데, 그러면 이미 가입한 회원이 "카카오 로그인"을 누를 때도 매번 아이디/비번을
// 다시 쳐야 해서 - 이미 브라우저에 세션이 있으면 클릭 한 번으로 끝나는 구글/일반적인 SSO 경험과 달라짐 -
// 오히려 불편하다는 피드백을 받고 아래처럼 범위를 좁혔다.
//
//   - 로그인(SocialLoginIntent.LOGIN) : prompt를 안 붙임 → 카카오 기본 동작 그대로.
//     브라우저에 카카오 세션이 있으면 클릭 한 번으로 로그인됨(구글과 동일한 편의),
//     세션이 없으면 카카오가 알아서 로그인 화면을 보여줌.
//   - 회원가입(SocialLoginIntent.SIGNUP) : prompt=login을 붙여서 매번 카카오 로그인 화면을 강제로 띄움.
//     "새 계정을 만드는" 시점에만 재인증을 강제해서, 브라우저에 남아있는 다른 사람의 카카오 세션으로
//     실수로 가입돼버리는 걸 막는다. (구글의 "계정 선택" 같은 여러 계정 목록 UI는 카카오에 없음 -
//     카카오는 브라우저당 로그인 세션을 하나만 유지하는 구조라 재인증만 가능하고 목록 선택은 지원하지 않음.
//     이건 카카오 플랫폼 자체의 한계라 우리 쪽 코드로 만들어줄 수 없다.)
//
// 어떤 intent인지는 SocialLoginEntryController.startSignup/startLogin이 이 리다이렉트 직전에
// 세션에 심어둔 SESSION_KEY_SOCIAL_LOGIN_INTENT 값을 그대로 읽어서 판단한다(추가 상태 없음).
@Component
public class KakaoLoginPromptAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

	private static final String KAKAO_REGISTRATION_ID = "kakao";

	private final OAuth2AuthorizationRequestResolver defaultResolver;

	public KakaoLoginPromptAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
		this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
				clientRegistrationRepository, "/oauth2/authorization");
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		return withKakaoPrompt(request, defaultResolver.resolve(request));
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
		return withKakaoPrompt(request, defaultResolver.resolve(request, clientRegistrationId));
	}

	private OAuth2AuthorizationRequest withKakaoPrompt(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
		if (authorizationRequest == null) {
			return null;
		}
		Object registrationId = authorizationRequest.getAttributes().get(OAuth2ParameterNames.REGISTRATION_ID);
		if (!KAKAO_REGISTRATION_ID.equals(registrationId) || !isSignupIntent(request)) {
			return authorizationRequest;
		}

		Map<String, Object> additionalParameters = new LinkedHashMap<>(authorizationRequest.getAdditionalParameters());
		additionalParameters.put("prompt", "login");

		return OAuth2AuthorizationRequest.from(authorizationRequest)
				.additionalParameters(additionalParameters)
				.build();
	}

	private boolean isSignupIntent(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return session != null
				&& SocialLoginIntent.SIGNUP == session.getAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT);
	}
}
