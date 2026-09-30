package megane6.weplanet.controller.email;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
	private final MessageSource messageSource;
	private final megane6.weplanet.i18n.Messages messages;

	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	@PostMapping("/signup/email/code")
	public Map<String, Object> sendCode(@RequestParam String email, HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		if (userRepository.existsByEmail(email)) {
			result.put("success", false);
			result.put("message", msg("signup.email.alreadyUsed"));
			return result;
		}
		try {
			emailVerificationService.sendVerificationCode(session, VerificationPurpose.SIGNUP, email);
			result.put("success", true);
			result.put("message", msg("signup.email.codeSent"));
		} catch (VerificationRateLimitException e) {
			result.put("success", false);
			result.put("message", messages.resolve(e));
		} catch (Exception e) {
			log.error("[회원가입] 이메일 발송 실패 (to={})", email, e);
			result.put("success", false);
			result.put("message", msg("signup.email.codeSendFailed"));
		}
		return result;
	}
	
	@PostMapping("/signup/email/verify")
	public Map<String, Object> verifyCode(@RequestParam String email, @RequestParam String code, HttpSession session) {
		Map<String, Object> result = new HashMap<>();
		VerificationResult verified = emailVerificationService.verifyCode(session, VerificationPurpose.SIGNUP, email, code);
		result.put("success", verified.isSuccess());
		result.put("message", verified.isSuccess() ? msg("signup.email.verifySuccess") : messages.resolve(verified.failureMessage()));
		return result;
	}
}