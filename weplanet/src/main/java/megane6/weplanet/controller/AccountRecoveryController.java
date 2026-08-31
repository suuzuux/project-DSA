package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.service.AccountRecoveryService;
import megane6.weplanet.service.email.EmailVerificationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AccountRecoveryController {
	
	private final AccountRecoveryService accountRecoveryService;
	private final EmailVerificationService emailVerificationService;
	
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
			result.put("message", "일치하는 회원정보를 찾을 수 없습니다.");
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(email);
			result.put("success", true);
			result.put("message", "인증코드를 보냈습니다. 메일함(스팸함 포함)을 확인해주세요.");
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
		}
		return result;
	}
	
	@PostMapping("/find-id/verify")
	@ResponseBody
	public Map<String, Object> verifyFindId(@RequestParam String realName, @RequestParam String email, @RequestParam String code) {
		Map<String, Object> result = new HashMap<>();
		if (!emailVerificationService.verifyCode(email, code)) {
			result.put("success", false);
			result.put("message", "인증코드가 일치하지 않거나 만료되었습니다.");
			return result;
		}
		if (!accountRecoveryService.matchesRealNameAndEmail(realName, email)) {
			result.put("success", false);
			result.put("message", "일치하는 회원정보를 찾을 수 없습니다.");
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
			result.put("message", "일치하는 회원정보를 찾을 수 없습니다.");
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(email);
			result.put("success", true);
			result.put("message", "인증코드를 보냈습니다. 메일함(스팸함 포함)을 확인해주세요.");
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
		}
		return result;
	}
	
	@PostMapping("/find-password/verify")
	@ResponseBody
	public Map<String, Object> verifyResetCode(@RequestParam String username, @RequestParam String email, @RequestParam String code) {
		Map<String, Object> result = new HashMap<>();
		if (!emailVerificationService.verifyCode(email, code)) {
			result.put("success", false);
			result.put("message", "인증코드가 일치하지 않거나 만료되었습니다.");
			return result;
		}
		result.put("success", true);
		result.put("message", "인증이 완료되었습니다. 새 비밀번호를 입력해주세요.");
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
			result.put("message", "이메일 인증을 먼저 완료해주세요.");
			return result;
		}
		try {
			accountRecoveryService.resetPassword(username, email, newPassword, confirmPassword);
			emailVerificationService.clear(email);
			result.put("success", true);
			result.put("message", "비밀번호가 변경되었습니다. 다시 로그인해주세요.");
		} catch (IllegalArgumentException e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
}