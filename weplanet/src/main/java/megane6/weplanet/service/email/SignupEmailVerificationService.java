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

// [이메일 인증코드] 이메일로 6자리 코드를 보내고, 입력받은 코드가 맞는지 확인하는 서비스.
// DB 테이블 없이 메모리(세션)에 5분짜리 코드로만 들고 있는 단순한 방식 - 서버 재시작하면 인증 상태가 초기화됨.
// (코드는 발송 후 5분 안에 입력해야 하고, 확인에 성공하면 그때부터 30분 동안 인증 상태가 유지된다)
// SETTINGS-03 커밋5: 가입 전(또는 로그인 전)이라 회원 선호 언어를 알 수 없으므로, 요청 시점의 로케일로 메일을 만든다.
//
// AUTH-11 보안 보완
//  1) 코드 입력을 5회 틀리면 그 코드는 폐기 - 6자리(100만 가지)를 무작정 대입해 보는 공격 방지
//  2) 같은 이메일로는 60초 안에 다시 보낼 수 없고, 하루 10회까지만 발송 - 메일 폭탄 / Gmail 발송 한도 소진 방지
//     (+ 같은 IP 10분 5통·하루 20통, 사이트 전체 하루 400통 - reserveSend 참고)
//  3) 인증 결과를 "세션 + 용도 + 이메일"에 묶어서 저장 - 예전에는 이메일 주소 하나로만 전역 저장해서,
//     가입 화면에서 인증한 주소를 이메일 변경·비밀번호 찾기에 그대로 쓰거나 다른 사람 브라우저(세션)에서
//     인증한 결과를 가져다 쓸 수 있었다.
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupEmailVerificationService {
	
	private static final int CODE_LENGTH = 6;
	private static final long EXPIRE_MINUTES = 5;
	// 코드 확인에 성공한 뒤 그 인증으로 가입·비밀번호 재설정·이메일 변경을 마칠 수 있는 시간.
	// 예전에는 "코드 발송 후 5분"이 그대로 적용돼서, 인증을 마친 뒤 나머지 칸을 채우다 5분이 지나면
	// 화면에는 "인증 완료"가 떠 있는데도 저장할 때 "이메일 인증이 필요합니다"로 거절됐다.
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
	 * 인증코드를 만들어 메일로 보낸다. 회원가입·이메일 변경처럼 숨길 정보가 없는 곳에서 쓰고,
	 * 발송 실패를 화면에 바로 알려줄 수 있도록 요청 처리 중에 보낸다.
	 *
	 * @throws VerificationRateLimitException 발송 제한(같은 주소 60초·하루, 같은 IP, 사이트 전체 하루 한도)에 걸린 경우 (메일은 보내지 않음)
	 */
	public void sendVerificationCode(HttpSession session, VerificationPurpose purpose, String email) {
		mailSender.send(prepareCodeMail(session, purpose, email));
		log.debug("이메일 인증코드 발송: purpose={}, to={}", purpose, normalize(email));
	}

	/**
	 * 아이디·비밀번호 찾기·휴면 해제처럼 "계정이 있는지"가 드러나면 안 되는 곳에서 쓴다.
	 * 대상이면 실제로 보내고, 대상이 아니면 메일은 보내지 않지만 발송 제한은 똑같이 적용한다.
	 * → 응답 문구와 제한 동작이 같아서, 화면만 보고는 입력한 정보와 일치하는 계정이 있는지 알 수 없다. (AUTH-11)
	 * 메일 전송(SMTP)은 백그라운드로 보낸다 - 요청 처리 중에 보내면 대상일 때만 응답이 1~2초 늦어져서
	 * 응답 시간만 재도 계정이 있는지 알 수 있었다. (VerificationMailAsyncSender)
	 *
	 * @throws VerificationRateLimitException 발송 제한에 걸린 경우
	 */
	public void sendVerificationCodeIfEligible(HttpSession session, VerificationPurpose purpose, String email, boolean eligible) {
		if (eligible) {
			asyncMailSender.send(prepareCodeMail(session, purpose, email));
			log.debug("이메일 인증코드 발송(백그라운드): purpose={}", purpose);
			return;
		}
		String normalized = normalize(email);
		reserveSend(normalized, false);
		entries(session).remove(key(purpose, normalized));
		log.debug("인증코드 발송 대상 아님(계정 불일치) - 메일은 보내지 않음: purpose={}", purpose);
	}

	// 발송 제한 확인 → 코드 생성 → 세션에 저장 → 메일 제목/본문 작성(현재 요청의 언어)까지 하고, 보낼 메일을 돌려준다
	private SimpleMailMessage prepareCodeMail(HttpSession session, VerificationPurpose purpose, String email) {
		String normalized = normalize(email);
		reserveSend(normalized, true);

		String code = generateCode();
		entries(session).put(key(purpose, normalized),
				new VerificationEntry(code, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES), false, 0));

		Locale locale = LocaleContextHolder.getLocale();
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(email.trim());
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

	// 발송 제한을 확인하고, 통과하면 발송 1회로 기록한다. 세 가지 기준을 모두 본다.
	//  1) 받는 주소: 60초 재발송 제한, 하루 DAILY_SEND_LIMIT 통 (같은 사람에게 메일 폭탄 방지)
	//  2) 요청한 IP: IP_WINDOW_MINUTES 분에 IP_WINDOW_LIMIT 통, 하루 IP_DAILY_LIMIT 통 (모든 용도 합산)
	//     - 예전에는 1)만 있어서, 받는 주소를 바꿔 가며 수천 통을 보내게 할 수 있었다
	//  3) 사이트 전체: 하루 SERVICE_DAILY_LIMIT 통 - Gmail 하루 발송 한도(개인 계정 약 500통)를 다 써서
	//     그날 모든 인증 메일이 막히거나 스팸 발송으로 계정이 정지되는 것을 막는 마지막 안전장치
	// willSend: 실제로 메일을 보내는지 (찾기에서 계정이 일치하지 않으면 false). 제한 확인은 똑같이 하고(응답이 같아야
	// 계정 존재가 안 드러남), 사이트 전체 한도는 실제로 보낸 메일만 센다.
	// 여러 기준을 한 번에 확인하고 기록해야 해서 잠금 하나로 묶는다 (동시에 요청이 와도 한도를 넘지 않게).
	private void reserveSend(String email, boolean willSend) {
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

			sendHistory.put(email, new SendHistory(today, todayCount + 1, now));
			ipSendHistory.put(ip, ipHistory.counted());
			if (willSend) {
				serviceDayCount++;
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
