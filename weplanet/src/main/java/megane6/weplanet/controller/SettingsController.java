package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.UserService;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
import org.springframework.web.servlet.LocaleResolver;
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
	private final MessageSource messageSource;
	private final LocaleResolver localeResolver;

	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}
	
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
			
			redirectAttributes.addFlashAttribute("profileMessage", msg("settings.profile.updateSuccess"));
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
			result.put("message", msg("settings.modal.emailRequired"));
			return result;
		}
		if (trimmed.equals(user.getEmail())) {
			result.put("success", false);
			result.put("message", msg("settings.modal.emailSameAsCurrent"));
			return result;
		}
		if (userRepository.existsByEmail(trimmed)) {
			result.put("success", false);
			result.put("message", msg("settings.email.alreadyInUse"));
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(trimmed);
			result.put("success", true);
			result.put("message", msg("settings.email.codeSent"));
		} catch (Exception e) {
			log.error("[회원정보 수정] 이메일 변경 인증코드 발송 실패 (to={})", trimmed, e);
			result.put("success", false);
			result.put("message", msg("settings.email.codeSendFailed"));
		}
		return result;
	}

	@PostMapping("/settings/email/verify")
	@ResponseBody
	public Map<String, Object> verifyEmailChangeCode(@RequestParam String newEmail, @RequestParam String code) {
		Map<String, Object> result = new HashMap<>();
		boolean verified = emailVerificationService.verifyCode(newEmail, code);
		result.put("success", verified);
		result.put("message", verified ? msg("settings.email.verifySuccess") : msg("settings.email.verifyFailed"));
		return result;
	}

	// [이벤트·혜택 알림 설정] type: marketing(광고성 정보) / email(커뮤니티 활동 이메일) / night(야간 알림)
	@PostMapping("/settings/notifications")
	@ResponseBody
	public Map<String, Object> updateNotificationPreference(@AuthenticationPrincipal AuthenticatedUser principal,
															 @RequestParam String type,
															 @RequestParam boolean enabled) {
		Map<String, Object> result = new HashMap<>();
		User user = userResolver.requireAuthenticated(principal);
		try {
			userService.updateNotificationPreference(user, type, enabled);
			result.put("success", true);
		} catch (IllegalArgumentException e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// [설정 - 언어 설정] "기본 서비스 언어" 저장 - 이 값이 게시글/댓글 AI 번역(TranslateService) 대상
	// 언어로도 그대로 쓰인다 (PostController.translatePost/translateComment 참고).
	@PostMapping("/settings/language")
	@ResponseBody
	public Map<String, Object> updateLanguage(@AuthenticationPrincipal AuthenticatedUser principal,
											  @RequestParam Language language,
											  HttpServletRequest request, HttpServletResponse response) {
		Map<String, Object> result = new HashMap<>();
		User user = userResolver.requireAuthenticated(principal);
		userService.updateLanguage(user, language);
		// SETTINGS-03 로케일 버그#1 수정: DB에만 저장하고 끝나면, 지금 이 세션의 실제 렌더링
		// 로케일(PreferredLocaleResolver)은 안 바뀌어서 페이지를 새로고침해도 화면 언어가 그대로였다.
		localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(language));
		result.put("success", true);
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