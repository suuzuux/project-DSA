package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.service.AccountRecoveryService;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
	
	private final AccountRecoveryService accountRecoveryService;
	private final SignupEmailVerificationService emailVerificationService;
	private final MessageSource messageSource;

	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	@GetMapping("/find-id")
	public String findIdForm() {
		return "find-id";
	}
	
	@PostMapping("/find-id/code")
	@ResponseBody
	public Map<String, Object> sendFindIdCode(@RequestParam String realName, @RequestParam String email) {
		Map<String, Object> result = new HashMap<>();
		if (!accountRecoveryService.matchesRealNameAndEmail(realName, email)) {
			result.put("success", false);
			result.put("message", msg("findId.noMatch"));
			return result;
		}
		if (!accountRecoveryService.isEligibleForRecovery(email)) {
			result.put("success", false);
			result.put("message", msg("findId.notEligible"));
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(email);
			result.put("success", true);
			result.put("message", msg("findId.codeSent"));
		} catch (Exception e) {
			log.error("[아이디 찾기] 이메일 발송 실패 (to={})", email, e);
			result.put("success", false);
			result.put("message", msg("findId.codeSendFailed"));
		}
		return result;
	}
	
	@PostMapping("/find-id/verify")
	@ResponseBody
	public Map<String, Object> verifyFindId(@RequestParam String realName, @RequestParam String email, @RequestParam String code) {
		Map<String, Object> result = new HashMap<>();
		if (!emailVerificationService.verifyCode(email, code)) {
			result.put("success", false);
			result.put("message", msg("findId.codeInvalid"));
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
		emailVerificationService.clear(email);
		return result;
	}
	
	@GetMapping("/find-password")
	public String findPasswordForm() {
		return "reset-password";
	}
	
	@PostMapping("/find-password/code")
	@ResponseBody
	public Map<String, Object> sendResetCode(@RequestParam String username, @RequestParam String email) {
		Map<String, Object> result = new HashMap<>();
		if (!accountRecoveryService.matchesUsernameAndEmail(username, email)) {
			result.put("success", false);
			result.put("message", msg("resetPassword.noMatch"));
			return result;
		}
		if (!accountRecoveryService.isEligibleForRecovery(email)) {
			result.put("success", false);
			result.put("message", msg("resetPassword.notEligible"));
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(email);
			result.put("success", true);
			result.put("message", msg("resetPassword.codeSent"));
		} catch (Exception e) {
			log.error("[비밀번호 재설정] 이메일 발송 실패 (to={})", email, e);
			result.put("success", false);
			result.put("message", msg("resetPassword.codeSendFailed"));
		}
		return result;
	}
	
	@PostMapping("/find-password/verify")
	@ResponseBody
	public Map<String, Object> verifyResetCode(@RequestParam String username, @RequestParam String email, @RequestParam String code) {
		Map<String, Object> result = new HashMap<>();
		if (!emailVerificationService.verifyCode(email, code)) {
			result.put("success", false);
			result.put("message", msg("resetPassword.codeInvalid"));
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
											 @RequestParam String confirmPassword) {
		Map<String, Object> result = new HashMap<>();
		// 화면(JS)에서 인증 후에만 이 단계로 넘어가지만, 직접 POST를 우회하는 걸 막기 위해 서버에서도 확인한다
		if (!emailVerificationService.isVerified(email) || !accountRecoveryService.matchesUsernameAndEmail(username, email)) {
			result.put("success", false);
			result.put("message", msg("resetPassword.verifyRequired"));
			return result;
		}
		try {
			accountRecoveryService.resetPassword(username, email, newPassword, confirmPassword);
			emailVerificationService.clear(email);
			result.put("success", true);
			result.put("message", msg("resetPassword.resetSuccess"));
		} catch (IllegalArgumentException e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
}