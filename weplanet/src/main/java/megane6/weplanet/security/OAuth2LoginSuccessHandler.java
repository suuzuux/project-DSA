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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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
	
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
	
	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
										Authentication authentication) throws IOException, ServletException {
		
		OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
		// Phase 1: 구글(OIDC)만 지원. sub = 구글 계정 고유 ID, email/name = 구글이 제공하는 이메일/이름
		String providerId = oAuth2User.getAttribute("sub");
		String email = oAuth2User.getAttribute("email");
		String name = oAuth2User.getAttribute("name");
		
		Optional<User> existingUser = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId);
		
		User user;
		if (existingUser.isPresent()) {
			user = existingUser.get();
		} else {
			HttpSession session = request.getSession(false);
			SocialLoginIntent intent = session != null
					? (SocialLoginIntent) session.getAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT)
					: null;
			
			if (intent != SocialLoginIntent.SIGNUP) {
				// 로그인 페이지에서 눌렀는데 가입된 계정이 없는 경우 -> 자동 생성하지 않고 안내 화면으로
				response.sendRedirect("/login?socialNotFound=true");
				return;
			}
			
			user = createNewSocialUser(email, name, providerId);
		}
		
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.removeAttribute(SocialLoginEntryController.SESSION_KEY_SOCIAL_LOGIN_INTENT);
		}
		
		user.recordLogin();
		swapPrincipalIntoSecurityContext(user, request, response);
		
		response.sendRedirect("/");
	}
	
	private User createNewSocialUser(String email, String name, String providerId) {
		String username = usernameGenerator.generate(AuthProvider.GOOGLE);
		String nickname = nicknameGenerator.generate();
		String encodedPassword = passwordEncoder.encode(UUID.randomUUID().toString());
		
		User newUser = User.createSocialFan(username, encodedPassword, name, nickname, email, AuthProvider.GOOGLE, providerId);
		return userRepository.save(newUser);
	}
	
	private void swapPrincipalIntoSecurityContext(User user, HttpServletRequest request, HttpServletResponse response) {
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