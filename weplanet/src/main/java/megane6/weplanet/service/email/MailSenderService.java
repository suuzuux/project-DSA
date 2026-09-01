package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailSenderService {
	private final JavaMailSender mailSender;
	
	// 발신자 주소 (application.properties의 spring.mail.username 사용)
	@Value("${spring.mail.username}")
	private String from;
	
	/**
	 * 최고관리자 로그인 인증번호 발송
	 * @param toEmail			받는 사람
	 * @param code				6자리 인증번호 (로그에 남기지 X)
	 * @param expireMinutes		유효시간 (분)
	 */
	public void sendAdminLoginCode(String toEmail, String code, long expireMinutes) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(toEmail);
		message.setSubject("[WePlaNet] 최고관리자 로그인 인증번호");
		message.setText("""
				최고관리자 로그인 인증번호입니다.
				
				인증번호 : %s
				
				%d분 이내에 입력해주세요.
				본인이 요청하지 않았다면 계정 비밀번호를 즉시 변경해주세요.
				""".formatted(code, expireMinutes));
		
		mailSender.send(message);
		log.info("[관리자 로그인] 인증번호 발송 완료: {}", maskEmail(toEmail));
	}
	
	// 이메일 가운데 숨김처리
	private String maskEmail(String email) {
		int at = email.indexOf("@");
		if (at <= 2) {
			return "***" + email.substring(Math.max(at, 0));
		}
		return email.substring(0, 2) + "***" + email.substring(at);
	}
}
