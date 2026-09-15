package megane6.weplanet.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.SocialLoginIntent;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.PendingSocialLink;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

// AUTH-10: 예전에 여기 있던 "이메일 중복 시 자동 연동 확인" 화면(email-conflict)과 "가입 직후 실명/이메일
// 입력" 화면(complete-profile)은 전부 없앴다. 소셜 로그인/가입 시작, 그리고 설정 화면에서 능동적으로
// 시작하는 연동(link) 플로우만 여기서 다룬다.
@Controller
@RequiredArgsConstructor
public class SocialLoginEntryController {

	public static final String SESSION_KEY_SOCIAL_LOGIN_INTENT = "SOCIAL_LOGIN_INTENT";
	// 설정 화면에서 "연결하기"를 눌러 소셜 연동을 시작할 때, 지금 로그인된 사용자가 누구인지 세션에 잠깐
	// 담아둔다. OAuth2 로그인 과정에서 SecurityContext가 소셜 원본 principal로 바뀌기 때문에, 콜백
	// (OAuth2LoginSuccessHandler)이 "누구의 계정에 연동해야 하는지"를 이 값으로 알아낸다.
	public static final String SESSION_KEY_LINK_TARGET_USER_ID = "SOCIAL_LINK_TARGET_USER_ID";
	// 이미 다른 소셜 계정이 연동돼 있는 상태에서 또 연동을 시도하면, 확인 화면을 띄우기 전까지
	// "무엇으로 바꾸려는 건지"를 잠깐 담아두는 용도.
	public static final String SESSION_KEY_PENDING_LINK = "PENDING_SOCIAL_LINK";

	private final AuthenticatedUserResolver userResolver;

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

	// 설정 화면의 "연결하기" 버튼이 여기로 들어온다. 로그인된 상태에서만 시작할 수 있다.
	@GetMapping("/social-login/{provider}/link")
	public String startLink(@PathVariable String provider, HttpSession session,
							@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return "redirect:/login";
		}
		session.setAttribute(SESSION_KEY_SOCIAL_LOGIN_INTENT, SocialLoginIntent.LINK);
		session.setAttribute(SESSION_KEY_LINK_TARGET_USER_ID, principal.getId());
		return "redirect:/oauth2/authorization/" + provider;
	}

	// 이미 다른 소셜 계정이 연동돼 있는 상태에서 또 연동을 시도했을 때 보여주는 확인 화면.
	// 세션에 담아둔 정보가 없으면(예: 이 URL로 바로 접근) 설정 화면으로 돌려보낸다.
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

	private PendingSocialLink pendingLink(HttpSession session) {
		return session != null
				? (PendingSocialLink) session.getAttribute(SESSION_KEY_PENDING_LINK)
				: null;
	}
}
