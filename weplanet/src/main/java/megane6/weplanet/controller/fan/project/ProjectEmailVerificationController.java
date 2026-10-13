package megane6.weplanet.controller.fan.project;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.account.EmailVerificationService;
import megane6.weplanet.service.project.ProjectService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/community/{artistId}/project/email-verification")
public class ProjectEmailVerificationController {
	private final EmailVerificationService evs;
	private final ProjectService ps;
	// 프로젝트 등록 모달의 이메일 인증 안내 문구 번역용
	private final megane6.weplanet.i18n.Messages messages;
	
	// 프로젝트 등록용 인증번호 전송
	@PostMapping
	public Map<String, Object> sendVerificationCode(
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		requireLogin(principal);

		String verificationKey = ps.sendProjectVerificationCode(principal.getId());

		// 인증번호 자체는 브라우저 응답에 포함 X
		return Map.of("success", true,
					  "verificationKey", verificationKey,
					  "message", messages.get("community.project.emailSent"));
	}
	
	// 사용자가 입력한 프로젝트 등록용 인증번호 확인
	@PostMapping("/confirm")
	public Map<String, Object> confirmVerificationCode(
			@RequestParam(required = false) String verificationKey,
			@RequestParam(required = false) String code,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		requireLogin(principal);
		
		if (code == null || !code.matches("^[0-9]{6}$")) {
			throw new IllegalArgumentException("community.project.js.emailCodeInvalid");
		}
		EmailVerificationService.VerificationResult result =
				evs.confirmProjectVerification(principal.getId(), verificationKey, code);
		if (!result.verified()) {
			return Map.of("success", false,
						  "remainingAttempts", result.remainingAttempts(),
					"message", messages.get("community.project.emailCodeMismatch"));
		}
		return Map.of("success", true,
					  "remainingAttempts", result.remainingAttempts(),
					  "message", messages.get("community.project.emailVerified"));
	}
	
	private void requireLogin(AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
	}
}
