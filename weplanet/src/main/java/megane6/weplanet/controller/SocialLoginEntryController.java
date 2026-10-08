package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.SocialLoginIntent;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.PendingSocialLink;
import megane6.weplanet.security.PendingSocialSignup;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.SocialSignupService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 소셜 로그인/가입 시작, 소셜 가입 확인 화면, 설정 화면에서 시작하는 소셜 연동(link) 흐름을 다룬다.
@Slf4j
@Controller
@RequiredArgsConstructor
public class SocialLoginEntryController {

	public static final String SESSION_KEY_SOCIAL_LOGIN_INTENT = "SOCIAL_LOGIN_INTENT";
	// 소셜 연동을 시작한 사용자 id (OAuth2 콜백이 연동 대상 계정을 찾을 때 사용).
	public static final String SESSION_KEY_LINK_TARGET_USER_ID = "SOCIAL_LINK_TARGET_USER_ID";
	// 다른 소셜 계정이 이미 연동된 상태에서 바꾸려는 연동 정보 (확인 전까지 보관).
	public static final String SESSION_KEY_PENDING_LINK = "PENDING_SOCIAL_LINK";
	// 가입 확인 전까지 보관하는 소셜 정보
	public static final String SESSION_KEY_PENDING_SIGNUP = "PENDING_SOCIAL_SIGNUP";

	private final AuthenticatedUserResolver userResolver;
	private final SocialSignupService socialSignupService;
	private final SocialLoginSessionSupport socialLoginSessionSupport;
	private final Messages messages;

	// 가입된 계정이 없는 소셜 계정의 가입 확인 화면 (정보가 없거나 10분이 지나면 로그인으로).
	@GetMapping("/social-login/signup-confirm")
	public String signupConfirmForm(HttpSession session, Model model) {
		PendingSocialSignup pending = pendingSignup(session);
		if (pending == null) {
			return "redirect:/login";
		}
		model.addAttribute("providerLabel", providerLabel(pending.provider()));
		model.addAttribute("socialEmail", pending.hasRealEmail() ? pending.email() : null);
		model.addAttribute("socialName", pending.suggestedNickname() != null && !pending.suggestedNickname().isBlank()
				? pending.suggestedNickname() : null);
		return "social-signup-confirm";
	}

	// 가입 확인 - 필수 약관을 확인하고 계정을 만든 뒤 바로 로그인한다.
	@PostMapping("/social-login/signup-confirm/confirm")
	public String confirmSignup(@RequestParam(defaultValue = "false") boolean agreeAge,
								@RequestParam(defaultValue = "false") boolean agreeTerms,
								@RequestParam(defaultValue = "false") boolean marketingConsent,
								HttpServletRequest request, HttpServletResponse response,
								HttpSession session, RedirectAttributes redirectAttributes) {
		PendingSocialSignup pending = pendingSignup(session);
		if (pending == null) {
			return "redirect:/login";
		}
		// 직접 요청 우회를 막기 위해 서버에서도 확인한다.
		if (!agreeAge || !agreeTerms) {
			redirectAttributes.addFlashAttribute("errorMessage", messages.get("socialSignup.error.termsRequired"));
			return "redirect:/social-login/signup-confirm";
		}
		User user;
		try {
			user = socialSignupService.signup(pending, marketingConsent);
		} catch (IllegalStateException e) {
			session.removeAttribute(SESSION_KEY_PENDING_SIGNUP);
			return "redirect:/login?socialEmailTaken=true";
		} catch (DataIntegrityViolationException e) {
			// 동시에 같은 이메일·소셜 계정이 저장된 경우 (유니크 제약)
			log.warn("소셜 회원가입 저장 충돌: {}", e.getMostSpecificCause().getMessage());
			session.removeAttribute(SESSION_KEY_PENDING_SIGNUP);
			return "redirect:/login?socialEmailTaken=true";
		}
		session.removeAttribute(SESSION_KEY_PENDING_SIGNUP);
		socialLoginSessionSupport.loginAs(user, request, response);
		return "redirect:/";
	}

	// [아니오] - 아무 계정도 만들지 않고 로그인 화면으로 돌아간다
	@PostMapping("/social-login/signup-confirm/cancel")
	public String cancelSignup(HttpSession session) {
		if (session != null) {
			session.removeAttribute(SESSION_KEY_PENDING_SIGNUP);
		}
		return "redirect:/login?socialSignupCancelled=true";
	}

	// 회원가입의 소셜 가입 버튼 - intent=SIGNUP 을 남기고 OAuth2 로그인을 시작한다.
	@GetMapping("/social-login/{provider}/signup")
	public String startSignup(@PathVariable String provider, HttpSession session) {
		session.setAttribute(SESSION_KEY_SOCIAL_LOGIN_INTENT, SocialLoginIntent.SIGNUP);
		return "redirect:/oauth2/authorization/" + provider;
	}

	// 로그인의 소셜 로그인 버튼 - intent=LOGIN 을 남기고 OAuth2 로그인을 시작한다.
	@GetMapping("/social-login/{provider}/login")
	public String startLogin(@PathVariable String provider, HttpSession session) {
		session.setAttribute(SESSION_KEY_SOCIAL_LOGIN_INTENT, SocialLoginIntent.LOGIN);
		return "redirect:/oauth2/authorization/" + provider;
	}

	// 설정 화면의 소셜 연결 버튼 (로그인 상태에서만).
	@GetMapping("/social-login/{provider}/link")
	public String startLink(@PathVariable String provider, HttpSession session,
							@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return "redirect:/login";
		}
		// 소셜 연결은 팬 계정만 가능하다.
		if (!"ROLE_FAN".equals(principal.getRoleName())) {
			return "redirect:/settings";
		}
		session.setAttribute(SESSION_KEY_SOCIAL_LOGIN_INTENT, SocialLoginIntent.LINK);
		session.setAttribute(SESSION_KEY_LINK_TARGET_USER_ID, principal.getId());
		return "redirect:/oauth2/authorization/" + provider;
	}

	// 다른 소셜 계정이 연동된 상태에서 새로 연동할 때의 확인 화면.
	@GetMapping("/social-login/link-confirm")
	public String linkConfirmForm(HttpSession session, Model model,
									@AuthenticationPrincipal AuthenticatedUser principal) {
		PendingSocialLink pending = pendingLink(session);
		if (pending == null) {
			return "redirect:/settings";
		}
		model.addAttribute("newProvider", pending.provider().name());
		if (principal != null) {
			User currentUser = userResolver.requireAuthenticated(principal);
			model.addAttribute("currentProvider", currentUser.getProvider() != null ? currentUser.getProvider().name() : null);
		}
		return "social-link-confirm";
	}

	// [예, 바꿉니다] - 기존 연동을 끊고 새 소셜 계정으로 교체한다.
	@PostMapping("/social-login/link-confirm/confirm")
	@Transactional
	public String confirmLink(@AuthenticationPrincipal AuthenticatedUser principal, HttpSession session) {
		PendingSocialLink pending = pendingLink(session);
		if (pending == null || principal == null) {
			return "redirect:/settings";
		}
		session.removeAttribute(SESSION_KEY_PENDING_LINK);
		User user = userResolver.requireAuthenticated(principal);
		user.linkSocialProvider(pending.provider(), pending.providerId());
		return "redirect:/settings?linked=true";
	}

	// [아니오] - 연동을 취소하고 기존 상태 그대로 둔다.
	@PostMapping("/social-login/link-confirm/cancel")
	public String cancelLink(HttpSession session) {
		if (session != null) {
			session.removeAttribute(SESSION_KEY_PENDING_LINK);
		}
		return "redirect:/settings";
	}

	private PendingSocialSignup pendingSignup(HttpSession session) {
		if (session == null) {
			return null;
		}
		Object value = session.getAttribute(SESSION_KEY_PENDING_SIGNUP);
		if (value instanceof PendingSocialSignup pending && !pending.isExpired()) {
			return pending;
		}
		session.removeAttribute(SESSION_KEY_PENDING_SIGNUP);
		return null;
	}

	// 화면 언어에 맞춘 소셜 서비스 이름 (socialSignup.provider.GOOGLE / KAKAO / LINE)
	private String providerLabel(AuthProvider provider) {
		return messages.get("socialSignup.provider." + provider.name());
	}

	private PendingSocialLink pendingLink(HttpSession session) {
		return session != null
				? (PendingSocialLink) session.getAttribute(SESSION_KEY_PENDING_LINK)
				: null;
	}
}
