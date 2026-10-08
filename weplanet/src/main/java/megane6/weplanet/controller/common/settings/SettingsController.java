package megane6.weplanet.controller.common.settings;
import megane6.weplanet.service.account.EmailVerificationService;
import megane6.weplanet.service.main.TranslateService;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.account.EmailChangeAuthService;
import megane6.weplanet.service.account.UserService;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import megane6.weplanet.service.email.SignupEmailVerificationService.VerificationResult;
import megane6.weplanet.service.email.VerificationPurpose;
import megane6.weplanet.service.email.VerificationRateLimitException;
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
	// 예외의 메시지 키를 화면 언어 문구로 바꿔 내보낼 때 쓴다
	private final megane6.weplanet.i18n.Messages messages;
	private final LocaleResolver localeResolver;
	private final EmailChangeAuthService emailChangeAuthService;

	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}
	
	@GetMapping("/settings")
	public String settings(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		User user = userResolver.requireAuthenticated(principal);
		model.addAttribute("user", user);
		return "common/settings/settings";
	}
	
	@PostMapping("/settings/profile")
	public String updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
								@RequestParam String nickname,
								@RequestParam String realName,
								@RequestParam String email,
								@RequestParam(required = false) String currentPassword,
								@RequestParam(required = false) String newPassword,
								@RequestParam(required = false) String confirmPassword,
								@RequestParam(required = false) String phone,
								HttpSession session,
								RedirectAttributes redirectAttributes) {
		User user = userResolver.requireAuthenticated(principal);
		try {
			// 이메일 변경 인증은 "이 세션에서, 이메일 변경 용도로" 받은 것만 인정한다
			boolean newEmailVerified = emailVerificationService.isVerified(session, VerificationPurpose.EMAIL_CHANGE, email);
			// 이메일 "수정하기"에서 현재 비밀번호 확인을 마쳤는지 (EmailChangeAuthService)
			boolean emailChangeAuthorized = emailChangeAuthService.isAuthorized(session, user);
			AuthenticatedUser refreshed = userService.updatePortalAccount(user, nickname, realName, email,
					newEmailVerified, emailChangeAuthorized, phone, currentPassword, newPassword, confirmPassword);
			// 저장까지 모두 성공한 뒤에 인증을 지운다 (비밀번호 검증 등에서 실패하면 인증을 다시 받지 않아도 되게)
			emailVerificationService.clear(session, VerificationPurpose.EMAIL_CHANGE, email);
			emailChangeAuthService.clear(session);
			Authentication current = SecurityContextHolder.getContext().getAuthentication();
			Authentication updated = new UsernamePasswordAuthenticationToken(
					refreshed, current.getCredentials(), refreshed.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(updated);
			
			redirectAttributes.addFlashAttribute("profileMessage", msg("settings.profile.updateSuccess"));
		} catch (IllegalArgumentException e) {
			log.warn("회원정보 수정 실패: {}", e.getMessage());
			redirectAttributes.addFlashAttribute("errorMessage", messages.resolve(e));
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			// 중복 확인과 저장 사이에 다른 계정이 같은 이메일을 먼저 쓴 경우 - 500 화면 대신 안내 문구를 보여준다
			log.warn("회원정보 수정 실패(이메일 중복 저장 충돌): {}", e.getMostSpecificCause().getMessage());
			redirectAttributes.addFlashAttribute("errorMessage", msg("settings.email.alreadyInUse"));
		}
		return "redirect:/settings";
	}
	
	// 이메일 "수정하기"를 누르면 먼저 현재 비밀번호를 확인한다. 맞으면 이 세션에 10분 동안 본인 확인 완료가 남고,
	// 화면은 잠긴 이메일 칸을 연다. (비밀번호가 없는 소셜 전용 계정은 화면에서 이 단계를 건너뛴다)
	@PostMapping("/settings/email/password-check")
	@ResponseBody
	public Map<String, Object> checkPasswordForEmailChange(@AuthenticationPrincipal AuthenticatedUser principal,
															@RequestParam(required = false) String currentPassword,
															HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		User user = userResolver.requireAuthenticated(principal);
		try {
			emailChangeAuthService.confirmPassword(session, user, currentPassword);
			result.put("success", true);
			result.put("message", msg("settings.email.passwordConfirmed"));
		} catch (IllegalArgumentException | IllegalStateException e) {
			result.put("success", false);
			result.put("message", messages.resolve(e));
		}
		return result;
	}

	@PostMapping("/settings/email/code")
	@ResponseBody
	public Map<String, Object> sendEmailChangeCode(@AuthenticationPrincipal AuthenticatedUser principal,
													@RequestParam String newEmail,
													HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		User user = userResolver.requireAuthenticated(principal);
		String trimmed = newEmail == null ? "" : newEmail.trim();

		// 소셜 연동 여부와 상관없이 누구나 이메일을 바꿀 수 있다.
		// 현재 비밀번호 확인을 먼저 마쳐야 인증코드를 보낸다 (로그인된 브라우저로 남의 이메일에 코드를 보내는 것 방지)
		if (!emailChangeAuthService.isAuthorized(session, user)) {
			result.put("success", false);
			result.put("needsPassword", true);
			result.put("message", msg("settings.email.passwordCheckRequired"));
			return result;
		}
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
			emailVerificationService.sendVerificationCode(session, VerificationPurpose.EMAIL_CHANGE, trimmed);
			result.put("success", true);
			result.put("message", msg("settings.email.codeSent"));
		} catch (VerificationRateLimitException e) {
			result.put("success", false);
			result.put("message", messages.resolve(e));
		} catch (Exception e) {
			log.error("[회원정보 수정] 이메일 변경 인증코드 발송 실패 (to={})", trimmed, e);
			result.put("success", false);
			result.put("message", msg("settings.email.codeSendFailed"));
		}
		return result;
	}

	@PostMapping("/settings/email/verify")
	@ResponseBody
	public Map<String, Object> verifyEmailChangeCode(@RequestParam String newEmail, @RequestParam String code,
													  HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		VerificationResult verified = emailVerificationService.verifyCode(session, VerificationPurpose.EMAIL_CHANGE, newEmail, code);
		result.put("success", verified.isSuccess());
		result.put("message", verified.isSuccess() ? msg("settings.email.verifySuccess") : messages.resolve(verified.failureMessage()));
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
			result.put("message", messages.resolve(e));
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
		// 관리자는 한국어 고정 - 언어를 바꾸지 않는다 (LanguageController 와 같은 규칙.
		// 화면은 PreferredLocaleResolver 가 어차피 한국어로 그리지만, 저장까지 막아야 메일·AI 번역 언어도 한국어로 유지된다)
		if ("ROLE_ADMIN".equals(principal.getRoleName())) {
			result.put("success", true);
			return result;
		}
		userService.updateLanguage(user, language);
		// DB 저장과 함께 지금 세션의 화면 언어도 바로 바꾼다 (안 하면 새로고침해도 화면 언어가 그대로다)
		localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(language));
		result.put("success", true);
		return result;
	}

	// [회원탈퇴] 소프트 삭제 처리 후 즉시 로그아웃시킨다 (세션에 남은 만료 계정으로 계속 요청이 오는 걸 막기 위함).
	@PostMapping("/settings/withdraw")
	public String withdraw(@AuthenticationPrincipal AuthenticatedUser principal,
						   @RequestParam(required = false) String currentPassword,
						   HttpServletRequest request,
						   RedirectAttributes redirectAttributes) {
		User user = userResolver.requireAuthenticated(principal);
		try {
			userService.withdraw(user, currentPassword);
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", messages.resolve(e));
			return "redirect:/settings";
		}
		SecurityContextHolder.clearContext();
		invalidateAndOpenFreshSession(request);
		return "redirect:/login/id?withdrawn";
	}

	// 소셜 연동 해제 - 비밀번호가 있는 계정만 가능하다 (없으면 로그인할 방법이 사라지므로 UserService 에서 막음).
	// 로그인 수단이 줄어드는 변경이라 해제 후에는 항상 로그아웃시킨다.
	@PostMapping("/settings/social/unlink")
	public String unlinkSocial(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request, HttpServletResponse response,
								RedirectAttributes redirectAttributes) {
		User user = userResolver.requireAuthenticated(principal);
		try {
			userService.unlinkSocialProvider(user);
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", messages.resolve(e));
			return "redirect:/settings";
		}
		socialLoginSessionSupport.clearSecurityContext(request, response);
		invalidateAndOpenFreshSession(request);
		return "redirect:/login/id?unlinked=true";
	}

	// 세션을 버린 직후 새 세션을 열어 둔다 - 안 그러면 이전 세션 쿠키 때문에 /login?expired 로 튕긴다.
	// 화면 언어도 새 세션에 이어 붙여 탈퇴·연동 해제 직후 로그인 화면이 한국어로 돌아가지 않게 한다.
	private static void invalidateAndOpenFreshSession(HttpServletRequest request) {
		PreferredLocaleResolver.invalidateSessionKeepingLocale(request);
	}
}