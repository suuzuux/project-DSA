package megane6.weplanet.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SocialSignupReauthAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

	private static final String KAKAO_REGISTRATION_ID = "kakao";
	private static final String LINE_REGISTRATION_ID = "line";

	private final OAuth2AuthorizationRequestResolver defaultResolver;

	public SocialSignupReauthAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
		this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
				clientRegistrationRepository, "/oauth2/authorization");
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		return withReauthPrompt(defaultResolver.resolve(request));
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
		return withReauthPrompt(defaultResolver.resolve(request, clientRegistrationId));
	}

	private OAuth2AuthorizationRequest withReauthPrompt(OAuth2AuthorizationRequest authorizationRequest) {
		if (authorizationRequest == null) {
			return authorizationRequest;
		}

		Object registrationId = authorizationRequest.getAttributes().get(OAuth2ParameterNames.REGISTRATION_ID);

		String reauthParamName;
		String reauthParamValue;
		if (KAKAO_REGISTRATION_ID.equals(registrationId)) {
			reauthParamName = "prompt";
			reauthParamValue = "login";
		} else if (LINE_REGISTRATION_ID.equals(registrationId)) {
			reauthParamName = "disable_auto_login";
			reauthParamValue = "true";
		} else {
			return authorizationRequest;
		}

		Map<String, Object> additionalParameters = new LinkedHashMap<>(authorizationRequest.getAdditionalParameters());
		additionalParameters.put(reauthParamName, reauthParamValue);

		return OAuth2AuthorizationRequest.from(authorizationRequest)
				.additionalParameters(additionalParameters)
				.build();
	}
}
