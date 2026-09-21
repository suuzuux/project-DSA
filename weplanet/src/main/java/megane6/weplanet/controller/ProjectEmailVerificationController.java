package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.EmailVerificationService;
import megane6.weplanet.service.ProjectService;
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
					  "message", "가입하신 이메일로 인증번호를 전송했습니다. 메일함을 확인해주세요.");
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
			throw new IllegalArgumentException("숫자 6자리 인증번호를 입력해주세요.");
		}
		EmailVerificationService.VerificationResult result =
				evs.confirmProjectVerification(principal.getId(), verificationKey, code);
		if (!result.verified()) {
			return Map.of("success", false,
						  "remainingAttempts", result.remainingAttempts(),
					"message", "인증번호가 일치하지 않습니다.");
		}
		return Map.of("success", true,
					  "remainingAttempts", result.remainingAttempts(),
					  "message", "이메일 인증이 완료되었습니다.");
	}
	
	private void requireLogin(AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
	}
}
