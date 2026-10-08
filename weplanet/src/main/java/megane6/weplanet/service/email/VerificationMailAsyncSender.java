package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// 인증코드 메일 백그라운드 발송 (응답 시간으로 계정 존재가 드러나지 않게).
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationMailAsyncSender {

	private final JavaMailSender mailSender;

	@Async("verificationMailExecutor")
	public void send(SimpleMailMessage message) {
		try {
			mailSender.send(message);
		} catch (Exception e) {
			// 이미 응답했으므로 로그만 남긴다.
			log.error("[인증 메일] 백그라운드 발송 실패", e);
		}
	}
}
