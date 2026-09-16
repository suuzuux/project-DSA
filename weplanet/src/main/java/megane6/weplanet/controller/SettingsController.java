package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.UserService;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SettingsController {
	
	private final AuthenticatedUserResolver userResolver;
	private final UserService userService;
	private final UserRepository userRepository;
	private final SignupEmailVerificationService emailVerificationService;
	private final SocialLoginSessionSupport socialLoginSessionSupport;
	
	@GetMapping("/settings")
	public String settings(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		User user = userResolver.requireAuthenticated(principal);
		model.addAttribute("user", user);
		return "settings";
	}
	
	@PostMapping("/settings/profile")
	public String updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
								@RequestParam String nickname,
								@RequestParam String realName,
								@RequestParam String email,
								@RequestParam(required = false) String currentPassword,
								@RequestParam(required = false) String newPassword,
								@RequestParam(required = false) String confirmPassword,
								RedirectAttributes redirectAttributes) {
		User user = userResolver.requireAuthenticated(principal);
		try {
			AuthenticatedUser refreshed = userService.updatePortalAccount(
					user, nickname, realName, email, currentPassword, newPassword, confirmPassword);
			Authentication current = SecurityContextHolder.getContext().getAuthentication();
			Authentication updated = new UsernamePasswordAuthenticationToken(
					refreshed, current.getCredentials(), refreshed.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(updated);
			
			redirectAttributes.addFlashAttribute("profileMessage", "회원정보가 수정되었습니다.");
		} catch (IllegalArgumentException e) {
			log.warn("회원정보 수정 실패: {}", e.getMessage());
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/settings";
	}
	
	@PostMapping("/settings/email/code")
	@ResponseBody
	public Map<String, Object> sendEmailChangeCode(@AuthenticationPrincipal AuthenticatedUser principal,
													@RequestParam String newEmail) {
		Map<String, Object> result = new HashMap<>();
		User user = userResolver.requireAuthenticated(principal);
		String trimmed = newEmail == null ? "" : newEmail.trim();

		// AUTH-10: "연동된 소셜 provider의 이메일이라 못 바꾼다"는 제약을 없앴다 - 제공자와 무관하게 누구나
		// 이메일을 바꿀 수 있다.
		if (trimmed.isBlank()) {
			result.put("success", false);
			result.put("message", "이메일을 입력해주세요.");
			return result;
		}
		if (trimmed.equals(user.getEmail())) {
			result.put("success", false);
			result.put("message", "현재 이메일과 같습니다.");
			return result;
		}
		if (userRepository.existsByEmail(trimmed)) {
			result.put("success", false);
			result.put("message", "이미 사용 중인 이메일입니다.");
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(trimmed);
			result.put("success", true);
			result.put("message", "인증코드를 보냈습니다. 메일함(스팸함 포함)을 확인해주세요.");
		} catch (Exception e) {
			log.error("[회원정보 수정] 이메일 변경 인증코드 발송 실패 (to={})", trimmed, e);
			result.put("success", false);
			result.put("message", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
		}
		return result;
	}

	@PostMapping("/settings/email/verify")
	@ResponseBody
	public Map<String, Object> verifyEmailChangeCode(@RequestParam String newEmail, @RequestParam String code) {
		Map<String, Object> result = new HashMap<>();
		boolean verified = emailVerificationService.verifyCode(newEmail, code);
		result.put("success", verified);
		result.put("message", verified ? "이메일 인증이 완료되었습니다." : "인증코드가 일치하지 않거나 만료되었습니다.");
		return result;
	}

	// [회원탈퇴] 소프트 삭제 처리 후 즉시 로그아웃시킨다 (세션에 남은 만료 계정으로 계속 요청이 오는 걸 막기 위함).
	@PostMapping("/settings/withdraw")
	public String withdraw(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
		User user = userResolver.requireAuthenticated(principal);
		userService.withdraw(user);
		SecurityContextHolder.clearContext();
		invalidateAndOpenFreshSession(request);
		return "redirect:/login/id?withdrawn";
	}

	// AUTH-10: 연동 해제. 비밀번호가 있는 계정만 해제할 수 있다(비밀번호가 없으면 해제 즉시 이 계정에
	// 로그인할 방법이 없어지므로 UserService에서 막는다 - 화면에서도 그 경우엔 버튼을 비활성화해둔다).
	// 정상적으로 해제되면 로그인 수단이 하나 줄어드는 변경이라 항상 로그아웃시킨다.
	@PostMapping("/settings/social/unlink")
	public String unlinkSocial(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request, HttpServletResponse response,
								RedirectAttributes redirectAttributes) {
		User user = userResolver.requireAuthenticated(principal);
		try {
			userService.unlinkSocialProvider(user);
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/settings";
		}
		socialLoginSessionSupport.clearSecurityContext(request, response);
		invalidateAndOpenFreshSession(request);
		return "redirect:/login/id?unlinked=true";
	}

	// AUTH-10: session.invalidate() 직후 바로 redirect만 하면, 브라우저가 들고 있는 이전 세션 쿠키가
	// 그대로 다음 요청에 실려 오고 Spring Security의 invalidSessionUrl(SecurityConfig)이 이를 무효
	// 세션으로 판단해서 원래 의도한 목적지 대신 "/login?expired=true"로 가로채 버린다.
	// invalidate() 직후 새 세션을 열어 응답에 유효한 세션 쿠키를 실어 보내면 이 문제를 막을 수 있다
	// (LoginSuccessHandler.clearAuthentication()과 동일한 패턴).
	private static void invalidateAndOpenFreshSession(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		request.getSession(true);
	}
}