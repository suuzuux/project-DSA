package megane6.weplanet.service.email;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// [이메일 인증코드] 이메일로 6자리 코드를 보내고, 입력받은 코드가 맞는지 확인하는 서비스.
// DB 테이블 없이 메모리(세션)에 5분짜리 코드로만 들고 있는 단순한 방식 - 서버 재시작하면 인증 상태가 초기화됨.
//
// AUTH-11 보안 보완
//  1) 코드 입력을 5회 틀리면 그 코드는 폐기 - 6자리(100만 가지)를 무작정 대입해 보는 공격 방지
//  2) 같은 이메일로는 60초 안에 다시 보낼 수 없고, 하루 10회까지만 발송 - 메일 폭탄 / Gmail 발송 한도 소진 방지
//  3) 인증 결과를 "세션 + 용도 + 이메일"에 묶어서 저장 - 예전에는 이메일 주소 하나로만 전역 저장해서,
//     가입 화면에서 인증한 주소를 이메일 변경·비밀번호 찾기에 그대로 쓰거나 다른 사람 브라우저(세션)에서
//     인증한 결과를 가져다 쓸 수 있었다.
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupEmailVerificationService {
	
	private static final int CODE_LENGTH = 6;
	private static final long EXPIRE_MINUTES = 5;
	static final int MAX_FAILED_ATTEMPTS = 5;
	static final long RESEND_COOLDOWN_SECONDS = 60;
	static final int DAILY_SEND_LIMIT = 10;
	
	// 인증 항목은 세션에 저장한다 → 다른 세션에서는 보이지 않고, 세션이 끝나면 같이 사라진다
	private static final String SESSION_ATTR = "weplanet.emailVerification";
	
	private final JavaMailSender mailSender;
	private final SecureRandom random = new SecureRandom();
	// 발송 횟수 제한은 세션과 무관하게 "받는 이메일" 기준으로 센다 (세션을 새로 만들어 우회하지 못하게)
	private final Map<String, SendHistory> sendHistory = new ConcurrentHashMap<>();
	
	/**
	 * 인증코드를 만들어 메일로 보낸다.
	 *
	 * @throws VerificationRateLimitException 60초 재발송 제한 또는 하루 발송 한도에 걸린 경우 (메일은 보내지 않음)
	 */
	public void sendVerificationCode(HttpSession session, VerificationPurpose purpose, String email) {
		String normalized = normalize(email);
		reserveSend(normalized);
		
		String code = generateCode();
		entries(session).put(key(purpose, normalized),
				new VerificationEntry(code, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES), false, 0));
		
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(email.trim());
		message.setSubject("[WePlaNet] 이메일 인증코드");
		message.setText("인증코드: " + code + "\n" + EXPIRE_MINUTES + "분 이내에 입력해주세요.");
		mailSender.send(message);
		
		log.debug("이메일 인증코드 발송: purpose={}, to={}", purpose, normalized);
	}
	
	/**
	 * 입력한 코드를 확인한다. 같은 세션·같은 용도로 발송된 코드만 비교하고, 5회 틀리면 코드를 폐기한다.
	 */
	public VerificationResult verifyCode(HttpSession session, VerificationPurpose purpose, String email, String inputCode) {
		if (session == null || email == null || inputCode == null) {
			return VerificationResult.INVALID;
		}
		Map<String, VerificationEntry> entries = entries(session);
		String key = key(purpose, normalize(email));
		VerificationResult[] result = {VerificationResult.INVALID};
		
		// compute 로 묶어서, 같은 세션에서 요청을 동시에 여러 개 보내도 틀린 횟수가 정확히 올라가게 한다
		entries.computeIfPresent(key, (k, entry) -> {
			if (entry.isExpired()) {
				return null;
			}
			if (matches(entry.code(), inputCode.trim())) {
				result[0] = VerificationResult.SUCCESS;
				return entry.verified();
			}
			VerificationEntry failed = entry.failedOnce();
			if (failed.failedAttempts() >= MAX_FAILED_ATTEMPTS) {
				log.warn("[이메일 인증] 코드 {}회 오입력으로 폐기: purpose={}, email={}", MAX_FAILED_ATTEMPTS, purpose, k);
				result[0] = VerificationResult.TOO_MANY_ATTEMPTS;
				return null;
			}
			return failed;
		});
		return result[0];
	}
	
	/** 같은 세션에서, 같은 용도로 인증을 마친 이메일인지 확인한다. */
	public boolean isVerified(HttpSession session, VerificationPurpose purpose, String email) {
		if (session == null || email == null) {
			return false;
		}
		VerificationEntry entry = entries(session).get(key(purpose, normalize(email)));
		return entry != null && entry.isVerified() && !entry.isExpired();
	}
	
	public void clear(HttpSession session, VerificationPurpose purpose, String email) {
		if (session == null || email == null) {
			return;
		}
		entries(session).remove(key(purpose, normalize(email)));
	}
	
	// 날짜가 지난 발송 기록은 하루 한도 계산에 더 이상 쓰이지 않으므로 매일 정리한다
	@Scheduled(cron = "0 30 4 * * *")
	public void purgeOldSendHistory() {
		LocalDate today = LocalDate.now();
		sendHistory.entrySet().removeIf(e -> e.getValue().date().isBefore(today));
	}
	
	// 60초 재발송 제한과 하루 발송 한도를 확인하고, 통과하면 발송 1회로 기록한다.
	// compute 안에서 확인과 기록을 한 번에 해서, 동시에 여러 요청이 와도 한도를 넘지 않게 한다.
	private void reserveSend(String email) {
		LocalDateTime now = LocalDateTime.now();
		LocalDate today = now.toLocalDate();
		sendHistory.compute(email, (k, history) -> {
			LocalDateTime lastSentAt = history == null ? null : history.lastSentAt();
			int todayCount = history == null || !history.date().equals(today) ? 0 : history.count();
			
			if (lastSentAt != null && lastSentAt.plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
				long waitSeconds = Duration.between(now, lastSentAt.plusSeconds(RESEND_COOLDOWN_SECONDS)).getSeconds() + 1;
				throw new VerificationRateLimitException(waitSeconds + "초 후에 인증코드를 다시 받을 수 있습니다.");
			}
			if (todayCount >= DAILY_SEND_LIMIT) {
				throw new VerificationRateLimitException(
						"오늘 인증코드 발송 횟수(" + DAILY_SEND_LIMIT + "회)를 모두 사용했습니다. 내일 다시 시도해주세요.");
			}
			return new SendHistory(today, todayCount + 1, now);
		});
	}
	
	@SuppressWarnings("unchecked")
	private Map<String, VerificationEntry> entries(HttpSession session) {
		Object existing = session.getAttribute(SESSION_ATTR);
		if (existing instanceof Map<?, ?> map) {
			return (Map<String, VerificationEntry>) map;
		}
		Map<String, VerificationEntry> created = new ConcurrentHashMap<>();
		session.setAttribute(SESSION_ATTR, created);
		return created;
	}
	
	private static String key(VerificationPurpose purpose, String normalizedEmail) {
		return purpose.name() + ":" + normalizedEmail;
	}
	
	private static String normalize(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
	
	// 문자열 비교에 걸리는 시간 차이로 코드를 추측하지 못하게 고정 시간 비교를 쓴다
	private static boolean matches(String expected, String input) {
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), input.getBytes(StandardCharsets.UTF_8));
	}
	
	private String generateCode() {
		return String.format("%0" + CODE_LENGTH + "d", random.nextInt(1_000_000));
	}
	
	/** 코드 확인 결과. 실패 문구는 화면에 그대로 보여준다. */
	public enum VerificationResult {
		SUCCESS(null),
		INVALID("인증코드가 일치하지 않거나 만료되었습니다."),
		TOO_MANY_ATTEMPTS("인증코드를 " + MAX_FAILED_ATTEMPTS + "회 잘못 입력했습니다. 인증코드를 다시 받아주세요.");
		
		private final String failureMessage;
		
		VerificationResult(String failureMessage) {
			this.failureMessage = failureMessage;
		}
		
		public boolean isSuccess() {
			return this == SUCCESS;
		}
		
		public String failureMessage() {
			return failureMessage;
		}
	}
	
	private record VerificationEntry(String code, LocalDateTime expiresAt, boolean isVerified, int failedAttempts)
			implements Serializable {
		boolean isExpired() {
			return LocalDateTime.now().isAfter(expiresAt);
		}
		VerificationEntry verified() {
			return new VerificationEntry(code, expiresAt, true, failedAttempts);
		}
		VerificationEntry failedOnce() {
			return new VerificationEntry(code, expiresAt, isVerified, failedAttempts + 1);
		}
	}
	
	private record SendHistory(LocalDate date, int count, LocalDateTime lastSentAt) {
	}
}
