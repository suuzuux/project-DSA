package megane6.weplanet.controller.common.auth;
import megane6.weplanet.service.account.EmailVerificationService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.security.LoginAttemptService;
import megane6.weplanet.security.UserSessionExpirer;
import megane6.weplanet.service.account.AccountRecoveryService;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import megane6.weplanet.service.email.SignupEmailVerificationService.VerificationResult;
import megane6.weplanet.service.email.VerificationPurpose;
import megane6.weplanet.service.email.VerificationRateLimitException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AccountRecoveryController {
	
	// 계정 존재 여부가 드러나지 않게 결과와 상관없이 같은 안내를 보여준다.
	
	private final AccountRecoveryService accountRecoveryService;
	private final SignupEmailVerificationService emailVerificationService;
	private final MessageSource messageSource;
	private final megane6.weplanet.i18n.Messages messages;
	private final UserSessionExpirer userSessionExpirer;
	private final LoginAttemptService loginAttemptService;

	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	@GetMapping("/find-id")
	public String findIdForm() {
		return "common/auth/find-id";
	}
	
	@PostMapping("/find-id/code")
	@ResponseBody
	public Map<String, Object> sendFindIdCode(@RequestParam String realName, @RequestParam String email, HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		// 일치하는 계정이 있으면 가입 이메일로 보낸다 (없으면 메일 없이 같은 응답).
		String recipient = accountRecoveryService.findIdRecipient(realName, email).orElse(null);
		try {
			emailVerificationService.sendVerificationCodeIfEligible(session, VerificationPurpose.FIND_ID, email, recipient);
			result.put("success", true);
			result.put("message", msg("recovery.codeSentIfMatched"));
		} catch (VerificationRateLimitException e) {
			result.put("success", false);
			result.put("message", messages.resolve(e));
		} catch (Exception e) {
			log.error("[아이디 찾기] 이메일 발송 실패 (to={})", email, e);
			result.put("success", false);
			result.put("message", msg("findId.codeSendFailed"));
		}
		return result;
	}
	
	@PostMapping("/find-id/verify")
	@ResponseBody
	public Map<String, Object> verifyFindId(@RequestParam String realName, @RequestParam String email, @RequestParam String code,
											HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		VerificationResult verified = emailVerificationService.verifyCode(session, VerificationPurpose.FIND_ID, email, code);
		if (!verified.isSuccess()) {
			result.put("success", false);
			result.put("message", messages.resolve(verified.failureMessage()));
			return result;
		}
		if (!accountRecoveryService.matchesRealNameAndEmail(realName, email)
				|| !accountRecoveryService.isEligibleForRecovery(email)) {
			result.put("success", false);
			result.put("message", msg("findId.noMatch"));
			return result;
		}
		result.put("success", true);
		result.put("username", accountRecoveryService.findUsernameByEmail(email));
		emailVerificationService.clear(session, VerificationPurpose.FIND_ID, email);
		return result;
	}
	
	@GetMapping("/find-password")
	public String findPasswordForm() {
		return "common/auth/reset-password";
	}
	
	@PostMapping("/find-password/code")
	@ResponseBody
	public Map<String, Object> sendResetCode(@RequestParam String username, @RequestParam String email, HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		// 아이디 + 이메일(대소문자 무시)이 맞으면 가입 때 등록한 주소로 보낸다
		String recipient = accountRecoveryService.resetPasswordRecipient(username, email).orElse(null);
		try {
			emailVerificationService.sendVerificationCodeIfEligible(session, VerificationPurpose.RESET_PASSWORD, email, recipient);
			result.put("success", true);
			result.put("message", msg("recovery.codeSentIfMatched"));
		} catch (VerificationRateLimitException e) {
			result.put("success", false);
			result.put("message", messages.resolve(e));
		} catch (Exception e) {
			log.error("[비밀번호 재설정] 이메일 발송 실패 (to={})", email, e);
			result.put("success", false);
			result.put("message", msg("resetPassword.codeSendFailed"));
		}
		return result;
	}
	
	@PostMapping("/find-password/verify")
	@ResponseBody
	public Map<String, Object> verifyResetCode(@RequestParam String username, @RequestParam String email, @RequestParam String code,
											   HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		VerificationResult verified = emailVerificationService.verifyCode(session, VerificationPurpose.RESET_PASSWORD, email, code);
		if (!verified.isSuccess()) {
			result.put("success", false);
			result.put("message", messages.resolve(verified.failureMessage()));
			return result;
		}
		result.put("success", true);
		result.put("message", msg("resetPassword.verifySuccess"));
		return result;
	}
	
	@PostMapping("/find-password/reset")
	@ResponseBody
	public Map<String, Object> resetPassword(@RequestParam String username,
											 @RequestParam String email,
											 @RequestParam String newPassword,
											 @RequestParam String confirmPassword,
											 HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		// 직접 POST 우회를 막기 위해 서버에서도 인증 여부를 확인한다.
		if (!emailVerificationService.isVerified(session, VerificationPurpose.RESET_PASSWORD, email)
				|| !accountRecoveryService.matchesUsernameAndEmail(username, email)) {
			result.put("success", false);
			result.put("message", msg("resetPassword.verifyRequired"));
			return result;
		}
		try {
			User user = accountRecoveryService.resetPassword(username, email, newPassword, confirmPassword);
			emailVerificationService.clear(session, VerificationPurpose.RESET_PASSWORD, email);
			// 비밀번호를 바꾸면 기존 세션을 모두 끊고 로그인 잠금을 푼다.
			userSessionExpirer.expireAllSessions(user.getId());
			loginAttemptService.reset(user.getUsername());
			result.put("success", true);
			result.put("message", msg("resetPassword.resetSuccess"));
		} catch (IllegalArgumentException e) {
			result.put("success", false);
			result.put("message", messages.resolve(e));
		}
		return result;
	}
}