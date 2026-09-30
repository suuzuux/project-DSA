package megane6.weplanet.controller.email;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import megane6.weplanet.service.email.SignupEmailVerificationService.VerificationResult;
import megane6.weplanet.service.email.VerificationPurpose;
import megane6.weplanet.service.email.VerificationRateLimitException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EmailVerificationController {
	
	private final SignupEmailVerificationService emailVerificationService;
	private final UserRepository userRepository;
	
	@PostMapping("/signup/email/code")
	public Map<String, Object> sendCode(@RequestParam String email, HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		if (userRepository.existsByEmail(email)) {
			result.put("success", false);
			result.put("message", "이미 가입에 사용된 이메일입니다.");
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(session, VerificationPurpose.SIGNUP, email);
			result.put("success", true);
			result.put("message", "인증코드를 보냈습니다. 메일함(스팸함 포함)을 확인해주세요.");
		} catch (VerificationRateLimitException e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		} catch (Exception e) {
			log.error("[회원가입] 이메일 발송 실패 (to={})", email, e);
			result.put("success", false);
			result.put("message", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
		}
		return result;
	}
	
	@PostMapping("/signup/email/verify")
	public Map<String, Object> verifyCode(@RequestParam String email, @RequestParam String code, HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		VerificationResult verified = emailVerificationService.verifyCode(session, VerificationPurpose.SIGNUP, email, code);
		result.put("success", verified.isSuccess());
		result.put("message", verified.isSuccess() ? "이메일 인증이 완료되었습니다." : verified.failureMessage());
		return result;
	}
}