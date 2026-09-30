package megane6.weplanet.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.service.AccountRecoveryService;
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
	
	// AUTH-11: 입력한 정보와 일치하는 계정이 있든 없든 같은 문구를 보여준다. 예전에는 "일치하는 회원정보를 찾을 수 없습니다" /
	// "비밀번호가 설정되어 있지 않아…" 처럼 경우마다 문구가 달라서, 이름+이메일(또는 아이디+이메일) 조합으로
	// 계정이 있는지, 소셜 전용 계정인지까지 알아낼 수 있었다.
	// (SETTINGS-03 병합: 문구는 메시지 키 recovery.codeSentIfMatched 로 옮김)
	
	private final AccountRecoveryService accountRecoveryService;
	private final SignupEmailVerificationService emailVerificationService;
	private final MessageSource messageSource;
	private final megane6.weplanet.i18n.Messages messages;

	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	@GetMapping("/find-id")
	public String findIdForm() {
		return "find-id";
	}
	
	@PostMapping("/find-id/code")
	@ResponseBody
	public Map<String, Object> sendFindIdCode(@RequestParam String realName, @RequestParam String email, HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		boolean eligible = accountRecoveryService.matchesRealNameAndEmail(realName, email)
				&& accountRecoveryService.isEligibleForRecovery(email);
		try {
			emailVerificationService.sendVerificationCodeIfEligible(session, VerificationPurpose.FIND_ID, email, eligible);
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
		return "reset-password";
	}
	
	@PostMapping("/find-password/code")
	@ResponseBody
	public Map<String, Object> sendResetCode(@RequestParam String username, @RequestParam String email, HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		boolean eligible = accountRecoveryService.matchesUsernameAndEmail(username, email)
				&& accountRecoveryService.isEligibleForRecovery(email);
		try {
			emailVerificationService.sendVerificationCodeIfEligible(session, VerificationPurpose.RESET_PASSWORD, email, eligible);
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
		// 화면(JS)에서 인증 후에만 이 단계로 넘어가지만, 직접 POST를 우회하는 걸 막기 위해 서버에서도 확인한다
		if (!emailVerificationService.isVerified(session, VerificationPurpose.RESET_PASSWORD, email)
				|| !accountRecoveryService.matchesUsernameAndEmail(username, email)) {
			result.put("success", false);
			result.put("message", msg("resetPassword.verifyRequired"));
			return result;
		}
		try {
			accountRecoveryService.resetPassword(username, email, newPassword, confirmPassword);
			emailVerificationService.clear(session, VerificationPurpose.RESET_PASSWORD, email);
			result.put("success", true);
			result.put("message", msg("resetPassword.resetSuccess"));
		} catch (IllegalArgumentException e) {
			result.put("success", false);
			result.put("message", messages.resolve(e));
		}
		return result;
	}
}