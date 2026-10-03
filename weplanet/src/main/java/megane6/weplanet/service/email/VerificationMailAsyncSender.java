package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// 인증코드 메일을 백그라운드에서 보낸다 (SignupEmailVerificationService.sendVerificationCodeIfEligible).
// 아이디·비밀번호 찾기 / 휴면 해제는 "일치하는 계정이 있을 때만" 메일을 보내는데, 요청 처리 중에 보내면 그때만 응답이
// 1~2초 늦어져서 응답 시간만 재도 계정이 있는지 알 수 있었다. 메일 제목/본문(사용자 언어)은 호출한 쪽에서 미리 만들고
// 여기서는 SMTP 전송만 한다 - 백그라운드 스레드에서는 요청의 언어 정보를 알 수 없기 때문.
// (@Async 는 다른 빈에서 불러야 동작하므로 별도 클래스로 둔다)
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
