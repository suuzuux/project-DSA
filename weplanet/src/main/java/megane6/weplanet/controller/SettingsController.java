package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.UserService;
import megane6.weplanet.service.email.SignupEmailVerificationService;
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
								@RequestParam(required = false) String phone,
								HttpSession session,
								RedirectAttributes redirectAttributes) {
		User user = userResolver.requireAuthenticated(principal);
		try {
			// AUTH-11: 이메일 변경 인증은 "이 세션에서, 이메일 변경 용도로" 받은 것만 인정한다
			boolean newEmailVerified = emailVerificationService.isVerified(session, VerificationPurpose.EMAIL_CHANGE, email);
			AuthenticatedUser refreshed = userService.updatePortalAccount(
					user, nickname, realName, email, newEmailVerified, phone, currentPassword, newPassword, confirmPassword);
			// 저장까지 모두 성공한 뒤에 인증을 지운다 (비밀번호 검증 등에서 실패하면 인증을 다시 받지 않아도 되게)
			emailVerificationService.clear(session, VerificationPurpose.EMAIL_CHANGE, email);
			Authentication current = SecurityContextHolder.getContext().getAuthentication();
			Authentication updated = new UsernamePasswordAuthenticationToken(
					refreshed, current.getCredentials(), refreshed.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(updated);
			
			redirectAttributes.addFlashAttribute("profileMessage", "회원정보가 수정되었습니다.");
		} catch (IllegalArgumentException e) {
			log.warn("회원정보 수정 실패: {}", e.getMessage());
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			// AUTH-11: 중복 확인과 저장 사이에 다른 계정이 같은 이메일을 먼저 쓴 경우 - DB 유니크 제약(uk_users_email)이
			// 막아 주고, 500 화면 대신 안내 문구를 보여준다
			log.warn("회원정보 수정 실패(이메일 중복 저장 충돌): {}", e.getMostSpecificCause().getMessage());
			redirectAttributes.addFlashAttribute("errorMessage", "이미 사용 중인 이메일입니다.");
		}
		return "redirect:/settings";
	}
	
	@PostMapping("/settings/email/code")
	@ResponseBody
	public Map<String, Object> sendEmailChangeCode(@AuthenticationPrincipal AuthenticatedUser principal,
													@RequestParam String newEmail,
													HttpSession session) {
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
			emailVerificationService.sendVerificationCode(session, VerificationPurpose.EMAIL_CHANGE, trimmed);
			result.put("success", true);
			result.put("message", "인증코드를 보냈습니다. 메일함(스팸함 포함)을 확인해주세요.");
		} catch (VerificationRateLimitException e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		} catch (Exception e) {
			log.error("[회원정보 수정] 이메일 변경 인증코드 발송 실패 (to={})", trimmed, e);
			result.put("success", false);
			result.put("message", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
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
		result.put("message", verified.isSuccess() ? "이메일 인증이 완료되었습니다." : verified.failureMessage());
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
											  @RequestParam Language language) {
		Map<String, Object> result = new HashMap<>();
		User user = userResolver.requireAuthenticated(principal);
		userService.updateLanguage(user, language);
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
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/settings";
		}
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