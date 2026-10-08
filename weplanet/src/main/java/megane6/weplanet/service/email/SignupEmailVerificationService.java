package megane6.weplanet.service.email;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

// 이메일 6자리 인증코드 발송·확인 (세션 저장, 5분 입력·30분 유지, 5회 실패 시 폐기, 발송 제한).
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupEmailVerificationService {
	
	private static final int CODE_LENGTH = 6;
	private static final long EXPIRE_MINUTES = 5;
	// 인증 성공 후 가입·재설정·이메일 변경을 마칠 수 있는 시간
	static final long VERIFIED_VALID_MINUTES = 30;
	static final int MAX_FAILED_ATTEMPTS = 5;
	static final long RESEND_COOLDOWN_SECONDS = 60;
	static final int DAILY_SEND_LIMIT = 10;
	static final long IP_WINDOW_MINUTES = 10;
	static final int IP_WINDOW_LIMIT = 5;
	static final int IP_DAILY_LIMIT = 20;
	static final int SERVICE_DAILY_LIMIT = 400;

	// 인증 항목은 세션에 저장한다.
	private static final String SESSION_ATTR = "weplanet.emailVerification";
	
	private final JavaMailSender mailSender;
	private final MessageSource messageSource;
	private final VerificationMailAsyncSender asyncMailSender;
	private final SecureRandom random = new SecureRandom();
	// 발송 제한은 이메일·IP·사이트 전체 기준으로 센다 (세션 우회 방지).
	private final Object sendLock = new Object();
	private final Map<String, SendHistory> sendHistory = new ConcurrentHashMap<>();
	private final Map<String, IpSendHistory> ipSendHistory = new ConcurrentHashMap<>();
	private LocalDate serviceDay = LocalDate.now();
	private int serviceDayCount = 0;
	
	/** 인증코드 발송 (가입·이메일 변경용, 요청 중 바로 발송). */
	public void sendVerificationCode(HttpSession session, VerificationPurpose purpose, String email) {
		String normalized = normalize(email);
		SendReservation reservation = reserveSend(normalized, true);
		try {
			mailSender.send(prepareCodeMail(session, purpose, normalized, email.trim()));
		} catch (RuntimeException e) {
			// 실제 발송이 실패하면 발송 기록과 코드를 되돌린다.
			releaseSend(reservation);
			entries(session).remove(key(purpose, normalized));
			throw e;
		}
		log.debug("이메일 인증코드 발송: purpose={}, to={}", purpose, normalized);
	}

	/** 계정 존재가 드러나면 안 되는 곳용 - 대상일 때만 백그라운드 발송하고 응답은 항상 같다. */
	public void sendVerificationCodeIfEligible(HttpSession session, VerificationPurpose purpose, String email, String recipient) {
		String normalized = normalize(email);
		reserveSend(normalized, recipient != null);
		if (recipient != null) {
			asyncMailSender.send(prepareCodeMail(session, purpose, normalized, recipient));
			log.debug("이메일 인증코드 발송(백그라운드): purpose={}", purpose);
			return;
		}
		entries(session).remove(key(purpose, normalized));
		log.debug("인증코드 발송 대상 아님(계정 불일치) - 메일은 보내지 않음: purpose={}", purpose);
	}

	// 코드 생성·세션 저장·메일 작성 후 보낼 메일을 반환한다.
	private SimpleMailMessage prepareCodeMail(HttpSession session, VerificationPurpose purpose, String normalizedEmail,
											  String recipient) {
		String code = generateCode();
		entries(session).put(key(purpose, normalizedEmail),
				new VerificationEntry(code, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES), false, 0));

		Locale locale = LocaleContextHolder.getLocale();
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(recipient);
		message.setSubject(messageSource.getMessage("mail.signupCode.subject", null, locale));
		message.setText(messageSource.getMessage("mail.signupCode.body", new Object[]{code, EXPIRE_MINUTES}, locale));
		return message;
	}
	
	/** 같은 세션·용도의 코드만 비교하고 5회 틀리면 폐기한다. */
	public VerificationResult verifyCode(HttpSession session, VerificationPurpose purpose, String email, String inputCode) {
		if (session == null || email == null || inputCode == null) {
			return VerificationResult.INVALID;
		}
		Map<String, VerificationEntry> entries = entries(session);
		String key = key(purpose, normalize(email));
		VerificationResult[] result = {VerificationResult.INVALID};
		
		// 동시 요청에도 실패 횟수가 정확하도록 compute 로 묶는다.
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
	
	/** 같은 세션·용도로 인증을 마친 이메일인지 */
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
	
	// 지난 발송 기록을 매일 정리한다.
	@Scheduled(cron = "0 30 4 * * *")
	public void purgeOldSendHistory() {
		LocalDate today = LocalDate.now();
		synchronized (sendLock) {
			sendHistory.entrySet().removeIf(e -> e.getValue().date().isBefore(today));
			ipSendHistory.entrySet().removeIf(e -> e.getValue().day().isBefore(today));
		}
	}

	// 발송 제한 확인 후 1회로 기록한다 (주소 60초·하루, IP 10분·하루, 사이트 하루).
	private SendReservation reserveSend(String email, boolean willSend) {
		LocalDateTime now = LocalDateTime.now();
		LocalDate today = now.toLocalDate();
		String ip = currentClientIp();
		synchronized (sendLock) {
			SendHistory history = sendHistory.get(email);
			LocalDateTime lastSentAt = history == null ? null : history.lastSentAt();
			int todayCount = history == null || !history.date().equals(today) ? 0 : history.count();
			if (lastSentAt != null && lastSentAt.plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
				long waitSeconds = Duration.between(now, lastSentAt.plusSeconds(RESEND_COOLDOWN_SECONDS)).getSeconds() + 1;
				throw new VerificationRateLimitException("verification.error.resendCooldown", waitSeconds);
			}
			if (todayCount >= DAILY_SEND_LIMIT) {
				throw new VerificationRateLimitException("verification.error.dailyLimit", DAILY_SEND_LIMIT);
			}

			IpSendHistory ipHistory = ipSendHistory.getOrDefault(ip, IpSendHistory.empty(now)).rolledOver(now);
			if (ipHistory.windowCount() >= IP_WINDOW_LIMIT || ipHistory.dayCount() >= IP_DAILY_LIMIT) {
				log.warn("[인증 메일] 같은 IP 의 요청이 많아 발송 제한: ip={}", ip);
				throw new VerificationRateLimitException("verification.error.tooManyRequests");
			}

			if (!serviceDay.equals(today)) {
				serviceDay = today;
				serviceDayCount = 0;
			}
			if (serviceDayCount >= SERVICE_DAILY_LIMIT) {
				log.warn("[인증 메일] 사이트 전체 하루 발송 한도({}통)에 도달해 오늘은 더 보내지 않습니다.", SERVICE_DAILY_LIMIT);
				throw new VerificationRateLimitException("verification.error.serviceLimit");
			}

			SendHistory reservedEmail = new SendHistory(today, todayCount + 1, now);
			IpSendHistory reservedIp = ipHistory.counted();
			SendReservation reservation = new SendReservation(email, history, reservedEmail,
					ip, ipSendHistory.get(ip), reservedIp, willSend ? today : null);
			sendHistory.put(email, reservedEmail);
			ipSendHistory.put(ip, reservedIp);
			if (willSend) {
				serviceDayCount++;
			}
			return reservation;
		}
	}

	// reserveSend 기록을 되돌린다 (다른 발송 기록은 유지).
	private void releaseSend(SendReservation reservation) {
		synchronized (sendLock) {
			if (sendHistory.get(reservation.email()) == reservation.reservedEmail()) {
				if (reservation.previousEmail() == null) {
					sendHistory.remove(reservation.email());
				} else {
					sendHistory.put(reservation.email(), reservation.previousEmail());
				}
			}
			if (ipSendHistory.get(reservation.ip()) == reservation.reservedIp()) {
				if (reservation.previousIp() == null) {
					ipSendHistory.remove(reservation.ip());
				} else {
					ipSendHistory.put(reservation.ip(), reservation.previousIp());
				}
			}
			if (reservation.serviceDay() != null && reservation.serviceDay().equals(serviceDay) && serviceDayCount > 0) {
				serviceDayCount--;
			}
		}
	}

	// 요청 IP (프록시 뒤에서도 실제 IP)
	private static String currentClientIp() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			return attributes.getRequest().getRemoteAddr();
		}
		return "unknown";
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
	
	// 타이밍 공격을 막기 위한 고정 시간 비교
	private static boolean matches(String expected, String input) {
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), input.getBytes(StandardCharsets.UTF_8));
	}
	
	private String generateCode() {
		return String.format("%0" + CODE_LENGTH + "d", random.nextInt(1_000_000));
	}
	
	/** 코드 확인 결과 (실패 문구는 메시지 키) */
	public enum VerificationResult {
		SUCCESS(null),
		INVALID("verification.error.invalid"),
		// "5회" 문구는 MAX_FAILED_ATTEMPTS 와 맞춘다.
		TOO_MANY_ATTEMPTS("verification.error.tooManyAttempts");
		
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
		// 인증 성공 후 VERIFIED_VALID_MINUTES 동안 유지
		VerificationEntry verified() {
			return new VerificationEntry(code, LocalDateTime.now().plusMinutes(VERIFIED_VALID_MINUTES), true, failedAttempts);
		}
		VerificationEntry failedOnce() {
			return new VerificationEntry(code, expiresAt, isVerified, failedAttempts + 1);
		}
	}
	
	private record SendHistory(LocalDate date, int count, LocalDateTime lastSentAt) {
	}

	// 발송 기록 전후 값 (되돌리기용)
	private record SendReservation(String email, SendHistory previousEmail, SendHistory reservedEmail,
								   String ip, IpSendHistory previousIp, IpSendHistory reservedIp, LocalDate serviceDay) {
	}

	// IP 별 발송 기록 (최근 구간 횟수 + 오늘 횟수)
	private record IpSendHistory(LocalDateTime windowStart, int windowCount, LocalDate day, int dayCount) {
		static IpSendHistory empty(LocalDateTime now) {
			return new IpSendHistory(now, 0, now.toLocalDate(), 0);
		}
		// 구간이 지났거나 날짜가 바뀌면 다시 센다.
		IpSendHistory rolledOver(LocalDateTime now) {
			boolean newWindow = !now.isBefore(windowStart.plusMinutes(IP_WINDOW_MINUTES));
			boolean newDay = !day.equals(now.toLocalDate());
			return new IpSendHistory(newWindow ? now : windowStart, newWindow ? 0 : windowCount,
					newDay ? now.toLocalDate() : day, newDay ? 0 : dayCount);
		}
		IpSendHistory counted() {
			return new IpSendHistory(windowStart, windowCount + 1, day, dayCount + 1);
		}
	}
}
