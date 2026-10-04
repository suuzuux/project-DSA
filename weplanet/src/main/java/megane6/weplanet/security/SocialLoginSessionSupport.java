package megane6.weplanet.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.List;

// 세션의 SecurityContext 를 우리 서비스 계정으로 채우거나(loginAs) 비우는 공용 로그인 처리.
// 소셜 로그인, 휴면 해제, 아티스트 멤버 프로필 로그인, 아이디 가입 직후 자동 로그인이 함께 쓴다.
@Component
@RequiredArgsConstructor
public class SocialLoginSessionSupport {

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
	private final SessionRegistry sessionRegistry;

	// 소셜 로그인을 거부/취소할 때, 세션에 이미 저장된 소셜 원본 인증(OAuth2User)을 빈 컨텍스트로 덮어 지운다.
	// (안 지우면 우리 계정 필드(nickname 등)가 없는 원본 principal 때문에 화면에서 500 오류가 난다)
	public void clearSecurityContext(HttpServletRequest request, HttpServletResponse response) {
		SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
		SecurityContextHolder.setContext(emptyContext);
		securityContextRepository.saveContext(emptyContext, request, response);
	}

	// 우리 서비스 계정(User)을 기준으로 SecurityContext를 채워서 실제 로그인 상태로 만든다.
	public void loginAs(User user, HttpServletRequest request, HttpServletResponse response) {
		AuthenticatedUser principal = AuthenticatedUser.builder()
				.id(user.getId())
				.username(user.getUsername())
				.password(user.getPassword())
				.nickname(user.getNickname())
				.roleName(user.getRole().authority())
				.enabled(user.isLoginable())
				.build();

		Authentication newAuth =
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

		// 폼 로그인에서 Spring Security 가 해 주는 세션 처리를 똑같이 한다.
		// 세션 id 교체(세션 고정 공격 방지) + 계정당 동시 로그인 1개 제한(다른 기기 세션 만료, 이 세션 등록).
		request.getSession(true);
		sessionAuthenticationStrategy().onAuthentication(newAuth, request, response);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(newAuth);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}

	private SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		ConcurrentSessionControlAuthenticationStrategy concurrent =
				new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
		concurrent.setMaximumSessions(1);   // SecurityConfig 의 maximumSessions(1) 과 같은 값
		concurrent.setExceptionIfMaximumExceeded(false);
		return new CompositeSessionAuthenticationStrategy(List.of(
				concurrent,
				new ChangeSessionIdAuthenticationStrategy(),
				new RegisterSessionAuthenticationStrategy(sessionRegistry)));
	}
}
