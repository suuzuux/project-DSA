package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.SocialLoginIntent;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.PendingSocialSignup;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.UserService;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SocialLoginEntryController {

	public static final String SESSION_KEY_SOCIAL_LOGIN_INTENT = "SOCIAL_LOGIN_INTENT";
	public static final String SESSION_KEY_PENDING_SOCIAL_SIGNUP = "PENDING_SOCIAL_SIGNUP";
	public static final String SESSION_KEY_PROFILE_COMPLETION_REQUIRED = "SOCIAL_PROFILE_COMPLETION_REQUIRED";
	private final UserRepository userRepository;
	private final SocialLoginSessionSupport socialLoginSessionSupport;
	private final AuthenticatedUserResolver userResolver;
	private final UserService userService;
	private final SignupEmailVerificationService emailVerificationService;

	// 회원가입 페이지의 "구글로 가입하기" 버튼이 여기로 들어온다.
	// intent=SIGNUP을 세션에 남긴 뒤, 스프링 시큐리티가 처리하는 진짜 OAuth2 로그인 시작 URL로 넘긴다.
	@GetMapping("/social-login/{provider}/signup")
	public String startSignup(@PathVariable String provider, HttpSession session) {
		session.setAttribute(SESSION_KEY_SOCIAL_LOGIN_INTENT, SocialLoginIntent.SIGNUP);
		return "redirect:/oauth2/authorization/" + provider;
	}

	// 로그인 페이지의 "구글로 로그인" 버튼이 여기로 들어온다.
	// intent=LOGIN을 세션에 남긴 뒤, 마찬가지로 OAuth2 로그인을 시작시킨다.
	@GetMapping("/social-login/{provider}/login")
	public String startLogin(@PathVariable String provider, HttpSession session) {
		session.setAttribute(SESSION_KEY_SOCIAL_LOGIN_INTENT, SocialLoginIntent.LOGIN);
		return "redirect:/oauth2/authorization/" + provider;
	}

	// 이메일이 겹치는 기존 계정이 있을 때 보여주는 확인 화면.
	// 세션에 담아둔 정보가 없으면(예: 이 URL로 바로 접근) 회원가입 화면으로 돌려보낸다.
	@GetMapping("/social-login/email-conflict")
	public String emailConflict(HttpSession session, Model model) {
		PendingSocialSignup pending = pendingSignup(session);
		if (pending == null) {
			return "redirect:/signup";
		}
		model.addAttribute("conflictEmail", pending.email());
		// 화면 문구("OO 로그인을 연동하시겠습니까?")가 provider별로 달라지도록 같이 내려준다.
		// 예전엔 이 화면 문구가 "구글"로 하드코딩돼 있었는데, 이 컨트롤러 로직 자체는 provider에 상관없이
		// 동작하므로(PendingSocialSignup.provider() 기반) 화면도 실제 provider 이름을 보여주는 게 맞다.
		model.addAttribute("conflictProvider", pending.provider().name());
		return "social-email-conflict";
	}

	// [예, 연동합니다] - 기존 계정에 소셜 로그인을 연동하고 그 계정으로 로그인 처리한다.
	@PostMapping("/social-login/email-conflict/confirm")
	@Transactional
	public String confirmEmailConflict(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
		PendingSocialSignup pending = pendingSignup(session);
		if (pending == null) {
			return "redirect:/signup";
		}
		session.removeAttribute(SESSION_KEY_PENDING_SOCIAL_SIGNUP);
		session.removeAttribute(SESSION_KEY_SOCIAL_LOGIN_INTENT);

		Optional<User> existing = userRepository.findByEmail(pending.email());
		if (existing.isEmpty()) {
			// 확인 화면 떠 있는 사이 그 계정이 탈퇴 등으로 사라진 경우 - 다시 회원가입부터 시키기
			return "redirect:/signup";
		}

		User user = existing.get();
		user.linkSocialProvider(pending.provider(), pending.providerId());
		user.recordLogin();
		socialLoginSessionSupport.loginAs(user, request, response);
		return "redirect:/";
	}

	// [아니오] - 계정을 만들지 않고 회원가입 실패로 안내한다.
	@PostMapping("/social-login/email-conflict/cancel")
	public String cancelEmailConflict(HttpSession session) {
		if (session != null) {
			session.removeAttribute(SESSION_KEY_PENDING_SOCIAL_SIGNUP);
			session.removeAttribute(SESSION_KEY_SOCIAL_LOGIN_INTENT);
		}
		return "redirect:/signup?socialEmailTaken=true";
	}

	// [가입 직후 한 번만] 카카오/LINE 신규가입 직후 실명/이메일을 입력받는 화면.
	// 세션에 SESSION_KEY_PROFILE_COMPLETION_REQUIRED 플래그가 없으면(이미 입력을 마쳤거나, 이 URL로 그냥 직접 들어온 경우)
	// 홈으로 돌려보낸다 - 완료 여부를 DB 컬럼으로 관리하지 않고 세션으로만 가볍게 처리하기로 했기 때문에,
	// 이 화면 자체가 "아직 입력 안 한 사람만" 의미 있게 보이도록 막아두는 것.
	@GetMapping("/social-login/complete-profile")
	public String completeProfileForm(@AuthenticationPrincipal AuthenticatedUser principal, HttpSession session, Model model) {
		if (principal == null || session == null
				|| !Boolean.TRUE.equals(session.getAttribute(SESSION_KEY_PROFILE_COMPLETION_REQUIRED))) {
			return "redirect:/";
		}
		User user = userResolver.requireAuthenticated(principal);
		model.addAttribute("user", user);
		// realName이 "닉네임을 이름으로 채워준 것"인지 "닉네임 자체가 없어서 고정 문구가 들어간 것"인지는
		// provider별로 다른 고정 문구(AuthProvider.placeholderRealName())와 비교해서 판단한다 - 예전엔
		// "카카오사용자" 문자열을 화면(social-complete-profile.html)에 그대로 하드코딩해서 LINE 로그인일 때
		// 문구("카카오 닉네임")도 틀리고 판단 로직도 틀리는 버그가 있었다(2026-09-03 실브라우저 테스트로 발견).
		boolean nicknamePrefilled = !user.getRealName().equals(user.getProvider().placeholderRealName());
		model.addAttribute("nicknamePrefilled", nicknamePrefilled);
		return "social-complete-profile";
	}

	// 실명/이메일 저장. 이메일이 기존 계정(주로 LOCAL, 혹은 다른 provider)과 겹치면 자동 연동하고,
	// 안 겹치면 지금 이 계정의 실명/이메일을 그대로 확정한다.
	//
	// [자동 연동이 안전한 이유] 이 화면은 ProfileCompletionRequiredFilter가 완료 전까지 다른 모든 페이지
	// 접근을 강제로 이리로 되돌리기 때문에, 지금 로그인돼 있는 이 소셜 계정(user)은 여태 게시글/채팅/후원 등
	// 어떤 실제 활동도 만든 적이 없음이 보장된다. 그래서 이메일이 겹치는 기존 계정을 발견하면, 이 placeholder
	// 계정은 그냥 지우고 그 기존 계정에 소셜 provider만 옮겨 붙이면 된다(구글이 가입 시점에 하는 것과 동일한
	// 처리를 카카오/LINE은 실명/이메일을 받는 이 시점에 하는 셈). 반대로 나중에 마이페이지 설정에서 이메일을
	// 바꾸는 경우는 이미 활동 이력이 있을 수 있어서 자동 연동하지 않고 지금처럼 그냥 막는다(UserService.updatePortalAccount).
	//
	// users 테이블에 (provider, provider_id) UNIQUE 제약이 있어서 순서가 중요하다: 기존 계정에 이 소셜
	// provider/provider_id를 옮겨 붙이기 "전에" 이 placeholder 계정을 먼저 지우고 flush해야 한다 - 안 그러면
	// 두 행이 순간적으로 같은 (provider, provider_id)를 갖게 돼서 유니크 제약 위반이 난다.
	@PostMapping("/social-login/complete-profile")
	@Transactional
	public String completeProfile(@AuthenticationPrincipal AuthenticatedUser principal,
									@RequestParam String realName,
									@RequestParam String email,
									HttpServletRequest request,
									HttpServletResponse response,
									HttpSession session,
									RedirectAttributes redirectAttributes) {
		if (principal == null || session == null
				|| !Boolean.TRUE.equals(session.getAttribute(SESSION_KEY_PROFILE_COMPLETION_REQUIRED))) {
			return "redirect:/";
		}
		User user = userResolver.requireAuthenticated(principal);
		String trimmedEmail = email == null ? "" : email.trim();

		Optional<User> existingUser = trimmedEmail.isBlank()
				? Optional.empty()
				: userRepository.findByEmail(trimmedEmail);

		if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
			if (!emailVerificationService.isVerified(trimmedEmail)) {
				redirectAttributes.addFlashAttribute("errorMessage", "이메일 인증을 먼저 완료해주세요.");
				return "redirect:/social-login/complete-profile";
			}
			User existing = existingUser.get();
			AuthProvider provider = user.getProvider();
			String providerId = user.getProviderId();

			userRepository.delete(user);
			userRepository.flush();
			existing.linkSocialProvider(provider, providerId);
			emailVerificationService.clear(trimmedEmail);

			session.removeAttribute(SESSION_KEY_PROFILE_COMPLETION_REQUIRED);
			existing.recordLogin();
			socialLoginSessionSupport.loginAs(existing, request, response);
			return "redirect:/";
		}

		try {
			userService.updatePortalAccount(user, user.getNickname(), realName, trimmedEmail, null, null, null);
		} catch (IllegalArgumentException e) {
			log.warn("가입 직후 실명/이메일 입력 실패: {}", e.getMessage());
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/social-login/complete-profile";
		}
		session.removeAttribute(SESSION_KEY_PROFILE_COMPLETION_REQUIRED);
		return "redirect:/";
	}

	// [가입 취소] 실명/이메일을 지금 입력하고 싶지 않은 사용자를 위한 탈출구.
	// 예전에 있었던 "다음에 하기"(스킵) 버튼과는 다르다 - 그건 placeholder 계정을 그대로 남겨서 나중에
	// 마저 입력하게 유도하는 방식이었는데, 그러면 자동 연동 로직이 기대는 전제("이 화면을 벗어나기 전에는
	// 이 소셜 계정이 실제 활동을 전혀 만들지 않았다")가 계속 안전하게 유지되도록 하기 어려워서 없앴다.
	// 이 취소 버튼은 대신 placeholder 계정 자체를 완전히 삭제하고 로그아웃시킨다 - 미완성 계정이
	// DB에 남지 않으니 나중에 이메일 충돌 등 복잡한 상황이 생길 여지가 없다. 원하면 언제든 같은 소셜
	// 계정으로 처음부터 다시 가입하면 된다.
	@PostMapping("/social-login/complete-profile/cancel")
	@Transactional
	public String cancelCompleteProfile(@AuthenticationPrincipal AuthenticatedUser principal,
										HttpServletRequest request,
										HttpServletResponse response,
										HttpSession session) {
		if (principal != null && session != null
				&& Boolean.TRUE.equals(session.getAttribute(SESSION_KEY_PROFILE_COMPLETION_REQUIRED))) {
			User user = userResolver.requireAuthenticated(principal);
			userRepository.delete(user);
		}
		socialLoginSessionSupport.clearSecurityContext(request, response);
		if (session != null) {
			session.invalidate();
		}
		return "redirect:/signup?socialSignupCancelled=true";
	}

	private PendingSocialSignup pendingSignup(HttpSession session) {
		return session != null
				? (PendingSocialSignup) session.getAttribute(SESSION_KEY_PENDING_SOCIAL_SIGNUP)
				: null;
	}
}
