package megane6.weplanet.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import megane6.weplanet.domain.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

// OAuth2LoginSuccessHandler와 소셜 로그인 관련 컨트롤러(SocialLoginEntryController 등)가
// 공통으로 필요로 하는 "세션의 SecurityContext를 우리 서비스 계정으로 채워넣기/비우기" 로직을 모아둔 헬퍼.
// 예전엔 OAuth2LoginSuccessHandler 안에 private 메서드로만 있었는데, 이메일 중복 확인 화면에서
// [예, 연동합니다]를 눌렀을 때도 같은 로그인 처리가 필요해져서 재사용 가능하도록 분리했다.
@Component
public class SocialLoginSessionSupport {

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	// 소셜 로그인을 거부/취소할 때, Spring Security가 이미 세션에 저장해버린
	// 소셜 플랫폼 원본 인증(OAuth2User/OidcUser principal)을 빈 컨텍스트로 덮어써서 지운다.
	// (안 지우면 index.html 등에서 우리 서비스 계정 필드(nickname 등)가 없는 원본 principal 때문에 500 에러가 남)
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

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(newAuth);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}
}
