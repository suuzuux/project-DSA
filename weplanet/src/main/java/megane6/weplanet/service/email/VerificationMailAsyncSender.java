package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// 인증코드 메일을 백그라운드에서 보낸다 - 찾기·휴면 해제는 대상일 때만 보내므로, 요청 중에 보내면 응답 시간으로 계정 존재가 드러난다.
// 메일 문구(사용자 언어)는 호출한 쪽에서 미리 만든다 (@Async 는 다른 빈에서 불러야 동작해서 별도 클래스).
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
			// 화면에는 이미 "일치하면 보냈습니다"로 응답했으므로 로그만 남긴다
			log.error("[인증 메일] 백그라운드 발송 실패", e);
		}
	}
}
