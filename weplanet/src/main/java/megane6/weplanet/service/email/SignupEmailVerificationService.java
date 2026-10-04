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

// 이메일 6자리 인증코드 발송·확인. 코드는 세션에 저장하고(5분 안에 입력), 확인되면 30분 동안 인증 상태가 유지된다.
// 5회 틀리면 코드 폐기, 발송 제한(주소·IP·사이트 전체), 인증 결과는 "세션 + 용도 + 이메일"에 묶어 저장한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupEmailVerificationService {
	
	private static final int CODE_LENGTH = 6;
	private static final long EXPIRE_MINUTES = 5;
	// 코드 확인에 성공한 뒤 그 인증으로 가입·비밀번호 재설정·이메일 변경을 마칠 수 있는 시간 (코드 입력 제한 5분과 별개)
	static final long VERIFIED_VALID_MINUTES = 30;
	static final int MAX_FAILED_ATTEMPTS = 5;
	static final long RESEND_COOLDOWN_SECONDS = 60;
	static final int DAILY_SEND_LIMIT = 10;
	static final long IP_WINDOW_MINUTES = 10;
	static final int IP_WINDOW_LIMIT = 5;
	static final int IP_DAILY_LIMIT = 20;
	static final int SERVICE_DAILY_LIMIT = 400;

	// 인증 항목은 세션에 저장한다 → 다른 세션에서는 보이지 않고, 세션이 끝나면 같이 사라진다
	private static final String SESSION_ATTR = "weplanet.emailVerification";
	
	private final JavaMailSender mailSender;
	private final MessageSource messageSource;
	private final VerificationMailAsyncSender asyncMailSender;
	private final SecureRandom random = new SecureRandom();
	// 발송 횟수 제한은 세션과 무관하게 "받는 이메일" / "요청한 IP" / "사이트 전체" 기준으로 센다 (세션을 새로 만들어 우회하지 못하게)
	private final Object sendLock = new Object();
	private final Map<String, SendHistory> sendHistory = new ConcurrentHashMap<>();
	private final Map<String, IpSendHistory> ipSendHistory = new ConcurrentHashMap<>();
	private LocalDate serviceDay = LocalDate.now();
	private int serviceDayCount = 0;
	
	/**
	 * 인증코드를 만들어 메일로 보낸다 - 회원가입·이메일 변경처럼 숨길 정보가 없는 곳에서 쓰고,
	 * 발송 실패를 화면에 바로 알릴 수 있도록 요청 처리 중에 보낸다.
	 *
	 * @throws VerificationRateLimitException 발송 제한(같은 주소 60초·하루, 같은 IP, 사이트 전체 하루 한도)에 걸린 경우 (메일은 보내지 않음)
	 */
	public void sendVerificationCode(HttpSession session, VerificationPurpose purpose, String email) {
		String normalized = normalize(email);
		SendReservation reservation = reserveSend(normalized, true);
		try {
			mailSender.send(prepareCodeMail(session, purpose, normalized, email.trim()));
		} catch (RuntimeException e) {
			// 메일이 실제로 나가지 않았으면 발송 기록(60초 제한·하루 횟수)을 되돌리고 저장한 코드도 지운다
			// (바로 다시 보낼 수 있게).
			releaseSend(reservation);
			entries(session).remove(key(purpose, normalized));
			throw e;
		}
		log.debug("이메일 인증코드 발송: purpose={}, to={}", purpose, normalized);
	}

	/**
	 * 아이디·비밀번호 찾기·휴면 해제처럼 계정이 있는지 드러나면 안 되는 곳에서 쓴다 - 대상일 때만 실제로 보내고,
	 * 응답 문구·발송 제한·응답 시간(백그라운드 발송)이 같아서 화면만으로는 계정 존재를 알 수 없다.
	 *
	 * @param email     사용자가 입력한 주소 - 발송 제한과 인증 확인은 이 값(대소문자 무시) 기준
	 * @param recipient 실제로 받을 주소(가입 때 등록한 주소). null 이면 대상이 아니라서 메일을 보내지 않는다
	 * @throws VerificationRateLimitException 발송 제한에 걸린 경우
	 */
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

	// 코드 생성 → 세션에 저장 → 메일 제목/본문 작성(현재 요청의 언어)까지 하고, 보낼 메일을 돌려준다 (발송 제한은 호출한 쪽에서 확인)
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
		synchronized (sendLock) {
			sendHistory.entrySet().removeIf(e -> e.getValue().date().isBefore(today));
			ipSendHistory.entrySet().removeIf(e -> e.getValue().day().isBefore(today));
		}
	}

	// 발송 제한 확인 후 발송 1회로 기록한다 - 받는 주소(60초·하루), 요청 IP(10분·하루), 사이트 전체(하루, Gmail 한도 보호).
	// willSend=false(찾기에서 계정 불일치)여도 제한은 똑같이 센다. 돌려준 값은 발송 실패 때 releaseSend 로 되돌리는 데 쓴다.
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

	// reserveSend 로 남긴 기록을 되돌린다. 그 사이에 같은 주소·IP 로 다른 발송이 기록됐으면 그 기록은 건드리지 않는다.
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

	// 요청한 사람의 IP. 터널(리버스 프록시) 뒤에서도 forward-headers-strategy 설정 덕분에 실제 접속자 IP 가 잡힌다.
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
	
	// 문자열 비교에 걸리는 시간 차이로 코드를 추측하지 못하게 고정 시간 비교를 쓴다
	private static boolean matches(String expected, String input) {
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), input.getBytes(StandardCharsets.UTF_8));
	}
	
	private String generateCode() {
		return String.format("%0" + CODE_LENGTH + "d", random.nextInt(1_000_000));
	}
	
	/** 코드 확인 결과. 실패 문구는 메시지 키 - 컨트롤러가 Messages.resolve()로 현재 로케일 문구로 바꿔 보여준다. */
	public enum VerificationResult {
		SUCCESS(null),
		INVALID("verification.error.invalid"),
		// 문구의 "5회"는 MAX_FAILED_ATTEMPTS 값과 맞춰 둔다
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
		// 인증에 성공하면 그 시점부터 VERIFIED_VALID_MINUTES 동안 인증 상태를 유지한다 (코드 입력 제한 5분과 별개)
		VerificationEntry verified() {
			return new VerificationEntry(code, LocalDateTime.now().plusMinutes(VERIFIED_VALID_MINUTES), true, failedAttempts);
		}
		VerificationEntry failedOnce() {
			return new VerificationEntry(code, expiresAt, isVerified, failedAttempts + 1);
		}
	}
	
	private record SendHistory(LocalDate date, int count, LocalDateTime lastSentAt) {
	}

	// 발송 1회로 기록하기 전/후의 값. serviceDay: 사이트 전체 횟수를 센 날 (실제로 보내지 않는 경우 null)
	private record SendReservation(String email, SendHistory previousEmail, SendHistory reservedEmail,
								   String ip, IpSendHistory previousIp, IpSendHistory reservedIp, LocalDate serviceDay) {
	}

	// IP 별 발송 기록: 최근 IP_WINDOW_MINUTES 분 묶음의 횟수 + 오늘 횟수
	private record IpSendHistory(LocalDateTime windowStart, int windowCount, LocalDate day, int dayCount) {
		static IpSendHistory empty(LocalDateTime now) {
			return new IpSendHistory(now, 0, now.toLocalDate(), 0);
		}
		// 묶음 시간이 지났거나 날짜가 바뀌었으면 그 횟수는 0부터 다시 센다
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
