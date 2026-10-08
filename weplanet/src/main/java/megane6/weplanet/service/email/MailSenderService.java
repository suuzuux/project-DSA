package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailSenderService {
	private final JavaMailSender mailSender;
	private final MessageSource messageSource;
	
	// 발신자 주소 (spring.mail.username)
	@Value("${spring.mail.username}")
	private String from;
	
	/** 관리자 로그인 인증번호 발송 (한국어 고정, 코드는 로그에 남기지 않음). */
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
	
	/** 팬 프로젝트 등록 인증번호 발송 (실패 시 예외로 인증 기록도 롤백, 요청 로케일 사용). */
	public void sendProjectVerificationCode(String toEmail, String code, long expireMinutes) {
		Locale locale = LocaleContextHolder.getLocale();
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(toEmail);
		message.setSubject(messageSource.getMessage("mail.projectCode.subject", null, locale));
		message.setText(messageSource.getMessage("mail.projectCode.body", new Object[]{code, expireMinutes}, locale));

		try {
			mailSender.send(message);
		} catch (MailException e) {
			log.warn("[프로젝트 등록] 인증번호 발송 실패: {}", maskEmail(toEmail), e);
			throw new IllegalStateException("mail.error.sendFailed");
		}
		log.info("[프로젝트 등록] 인증번호 발송 완료: {}", maskEmail(toEmail));
	}

	// 이메일 가운데 마스킹
	private String maskEmail(String email) {
		int at = email.indexOf("@");
		if (at <= 2) {
			return "***" + email.substring(Math.max(at, 0));
		}
		return email.substring(0, 2) + "***" + email.substring(at);
	}
}
