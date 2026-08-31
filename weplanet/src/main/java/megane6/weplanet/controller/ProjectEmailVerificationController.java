package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.EmailVerificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/community/{artistId}/project/email-verification")
public class ProjectEmailVerificationController {
	private final EmailVerificationService evs;
	
	// 프로젝트 등록용 인증번호 전송
	@PostMapping
	public Map<String, Object> sendVerificationCode(
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		requireLogin(principal);
		
		EmailVerificationService.IssuedVerification issued =
				evs.issueProjectVerification(principal.getId());
		
		// 실제 이메일 발송 Service를 연결하기 전까지 사용하는 모의 발송
		// 실제 이메일 발송 구현 후에는 반드시 이 로그 삭제
		log.info("[모의 이메일 인증] recipient={}, code={}", issued.recipientEmail(), issued.rawCode());
		
		// 인증번호 자체는 브라우저 응답에 포함 X
		return Map.of("success", true,
					  "verificationKey", issued.verificationKey(),
					  "message", "인증번호를 전송했습니다. 현재는 서버 콘솔에서 인증번호를 확인해주세요.");
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
