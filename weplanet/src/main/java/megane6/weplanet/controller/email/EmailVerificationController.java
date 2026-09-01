package megane6.weplanet.controller.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.email.SignupEmailVerificationService;
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
	public Map<String, Object> sendCode(@RequestParam String email) {
		Map<String, Object> result = new HashMap<>();
		if (userRepository.existsByEmail(email)) {
			result.put("success", false);
			result.put("message", "이미 가입에 사용된 이메일입니다.");
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(email);
			result.put("success", true);
			result.put("message", "인증코드를 보냈습니다. 메일함(스팸함 포함)을 확인해주세요.");
		} catch (Exception e) {
			log.error("[회원가입] 이메일 발송 실패 (to={})", email, e);
			result.put("success", false);
			result.put("message", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
		}
		return result;
	}
	
	@PostMapping("/signup/email/verify")
	public Map<String, Object> verifyCode(@RequestParam String email, @RequestParam String code) {
		Map<String, Object> result = new HashMap<>();
		boolean verified = emailVerificationService.verifyCode(email, code);
		result.put("success", verified);
		result.put("message", verified ? "이메일 인증이 완료되었습니다." : "인증코드가 일치하지 않거나 만료되었습니다.");
		return result;
	}
}