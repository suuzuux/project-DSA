package megane6.weplanet.controller.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
	private final MessageSource messageSource;

	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	@PostMapping("/signup/email/code")
	public Map<String, Object> sendCode(@RequestParam String email) {
		Map<String, Object> result = new HashMap<>();
		if (userRepository.existsByEmail(email)) {
			result.put("success", false);
			result.put("message", msg("signup.email.alreadyUsed"));
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(email);
			result.put("success", true);
			result.put("message", msg("signup.email.codeSent"));
		} catch (Exception e) {
			log.error("[회원가입] 이메일 발송 실패 (to={})", email, e);
			result.put("success", false);
			result.put("message", msg("signup.email.codeSendFailed"));
		}
		return result;
	}
	
	@PostMapping("/signup/email/verify")
	public Map<String, Object> verifyCode(@RequestParam String email, @RequestParam String code) {
		Map<String, Object> result = new HashMap<>();
		boolean verified = emailVerificationService.verifyCode(email, code);
		result.put("success", verified);
		result.put("message", verified ? msg("signup.email.verifySuccess") : msg("signup.email.verifyFailed"));
		return result;
	}
}