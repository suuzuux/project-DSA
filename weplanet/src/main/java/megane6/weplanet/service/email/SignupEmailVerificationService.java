package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// [회원가입 이메일 인증] 이메일로 6자리 코드를 보내고, 입력받은 코드가 맞는지 확인하는 서비스.
// DB 테이블 없이 메모리에 5분짜리 코드로만 들고 있는 가장 단순한 방식 - 서버 재시작하면 인증 상태가 초기화됨.
// SETTINGS-03 커밋5: 가입 전(또는 로그인 전)이라 회원 선호 언어를 알 수 없으므로, 요청 시점의 로케일로 메일을 만든다.
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupEmailVerificationService {
	
	private static final int CODE_LENGTH = 6;
	private static final long EXPIRE_MINUTES = 5;
	
	private final JavaMailSender mailSender;
	private final MessageSource messageSource;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, VerificationEntry> store = new ConcurrentHashMap<>();
	
	public void sendVerificationCode(String email) {
		String code = generateCode();
		store.put(email, new VerificationEntry(code, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES), false));
		
		Locale locale = LocaleContextHolder.getLocale();
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(email);
		message.setSubject(messageSource.getMessage("mail.signupCode.subject", null, locale));
		message.setText(messageSource.getMessage("mail.signupCode.body", new Object[]{code, EXPIRE_MINUTES}, locale));
		mailSender.send(message);
		
		log.debug("이메일 인증코드 발송: {}", email);
	}
	
	public boolean verifyCode(String email, String inputCode) {
		VerificationEntry entry = store.get(email);
		if (entry == null || entry.isExpired() || !entry.code().equals(inputCode)) {
			return false;
		}
		store.put(email, entry.verified());
		return true;
	}
	
	public boolean isVerified(String email) {
		VerificationEntry entry = store.get(email);
		return entry != null && entry.isVerified() && !entry.isExpired();
	}
	
	public void clear(String email) {
		store.remove(email);
	}
	
	private String generateCode() {
		return String.format("%0" + CODE_LENGTH + "d", random.nextInt(1_000_000));
	}
	
	private record VerificationEntry(String code, LocalDateTime expiresAt, boolean isVerified) {
		boolean isExpired() {
			return LocalDateTime.now().isAfter(expiresAt);
		}
		VerificationEntry verified() {
			return new VerificationEntry(code, expiresAt, true);
		}
	}
}
