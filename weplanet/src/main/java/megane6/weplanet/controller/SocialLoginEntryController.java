package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.SocialLoginIntent;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.PendingSocialSignup;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.UserService;
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

	// 회원가입 화면에서 소셜 로그인 시도했는데 같은 이메일의 기존 계정이 있어서
	// 연동할지 사용자 확인을 받아야 할 때, OAuth2LoginSuccessHandler가 여기에 구글 정보를 잠깐 담아둔다.
	public static final String SESSION_KEY_PENDING_SOCIAL_SIGNUP = "PENDING_SOCIAL_SIGNUP";

	// 카카오/LINE처럼 realName/email이 placeholder로 채워지는 provider로 "신규가입"한 직후에만 세워지는 플래그.
	// OAuth2LoginSuccessHandler가 세우고, 아래 completeProfile()에서 실제로 입력을 완료하면 지운다.
	// true인 동안에는 로그인 성공 시 홈 대신 /social-login/complete-profile로 보낸다.
	public static final String SESSION_KEY_PROFILE_COMPLETION_REQUIRED = "SOCIAL_PROFILE_COMPLETION_REQUIRED";

	private final UserRepository userRepository;
	private final SocialLoginSessionSupport socialLoginSessionSupport;
	private final AuthenticatedUserResolver userResolver;
	private final UserService userService;

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
		return "social-complete-profile";
	}

	// 실명/이메일 저장. 닉네임은 이 화면에서 건드리지 않으므로 기존 닉네임을 그대로 넘겨서
	// UserService.updatePortalAccount의 검증 로직(이메일 인증 완료 여부 포함)만 그대로 재사용한다.
	// 이메일을 실제로 바꾸는 경우엔 /settings/email/code, /settings/email/verify를 먼저 거쳐야 한다(설정 화면과 동일한 절차).
	@PostMapping("/social-login/complete-profile")
	public String completeProfile(@AuthenticationPrincipal AuthenticatedUser principal,
									@RequestParam String realName,
									@RequestParam String email,
									HttpSession session,
									RedirectAttributes redirectAttributes) {
		if (principal == null || session == null
				|| !Boolean.TRUE.equals(session.getAttribute(SESSION_KEY_PROFILE_COMPLETION_REQUIRED))) {
			return "redirect:/";
		}
		User user = userResolver.requireAuthenticated(principal);
		try {
			userService.updatePortalAccount(user, user.getNickname(), realName, email, null, null, null);
		} catch (IllegalArgumentException e) {
			log.warn("가입 직후 실명/이메일 입력 실패: {}", e.getMessage());
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/social-login/complete-profile";
		}
		session.removeAttribute(SESSION_KEY_PROFILE_COMPLETION_REQUIRED);
		return "redirect:/";
	}

	private PendingSocialSignup pendingSignup(HttpSession session) {
		return session != null
				? (PendingSocialSignup) session.getAttribute(SESSION_KEY_PENDING_SOCIAL_SIGNUP)
				: null;
	}
}
