package megane6.weplanet.service.account;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.exception.LocalizedIllegalArgumentException;
import megane6.weplanet.exception.LocalizedIllegalStateException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 설정 화면 "이메일 변경" 전 본인 확인 - 현재 비밀번호가 맞으면 이 세션에 10분 동안 확인 완료를 남긴다.
 * 인증코드 발송·최종 저장은 이 기록이 있어야 통과한다. 5회 틀리면 10분 잠금, 비밀번호가 없는 소셜 전용 계정은 항상 통과.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailChangeAuthService {

	static final long AUTH_VALID_MINUTES = 10;
	static final int MAX_FAILED_ATTEMPTS = 5;
	static final long LOCK_MINUTES = 10;

	private static final String SESSION_ATTR = "weplanet.emailChangeAuth";

	private final PasswordEncoder passwordEncoder;
	// userId → 틀린 횟수/잠금 해제 시각 (서버 메모리 - 재시작하면 초기화)
	private final Map<Long, FailedAttempts> failedAttempts = new ConcurrentHashMap<>();

	/** 이메일을 바꾸기 전에 비밀번호 확인이 필요한 계정인지 (비밀번호가 있는 계정만) */
	public boolean requiresPassword(User user) {
		return user.hasPassword();
	}

	/**
	 * 현재 비밀번호를 확인하고, 맞으면 이 세션에 본인 확인 완료를 기록한다.
	 *
	 * @throws IllegalArgumentException 비밀번호가 비었거나 틀린 경우 (메시지 키 - 컨트롤러가 Messages.resolve(e)로 번역)
	 * @throws IllegalStateException    5회 오입력으로 잠긴 경우
	 */
	public void confirmPassword(HttpSession session, User user, String password) {
		if (!requiresPassword(user)) {
			authorize(session, user);
			return;
		}
		FailedAttempts attempts = failedAttempts.get(user.getId());
		if (attempts != null && attempts.isLocked()) {
			throw lockedException();
		}
		if (password == null || password.isBlank()) {
			throw new IllegalArgumentException("settings.email.currentPasswordRequired");
		}
		if (!passwordEncoder.matches(password, user.getPassword())) {
			FailedAttempts updated = failedAttempts.compute(user.getId(), (id, current) ->
					(current == null || current.isExpiredLock()) ? FailedAttempts.first() : current.failedOnce());
			if (updated.isLocked()) {
				log.warn("[이메일 변경] 현재 비밀번호 {}회 오입력으로 잠금: userId={}", MAX_FAILED_ATTEMPTS, user.getId());
				throw lockedException();
			}
			throw new LocalizedIllegalArgumentException("settings.email.passwordIncorrectRemaining",
					MAX_FAILED_ATTEMPTS - updated.count());
		}
		failedAttempts.remove(user.getId());
		authorize(session, user);
	}

	/** 이 세션에서 이 계정으로 이메일 변경 본인 확인을 마쳤는지 (소셜 전용 계정은 항상 true) */
	public boolean isAuthorized(HttpSession session, User user) {
		if (!requiresPassword(user)) {
			return true;
		}
		if (session == null) {
			return false;
		}
		Object value = session.getAttribute(SESSION_ATTR);
		return value instanceof Authorization auth
				&& auth.userId().equals(user.getId())
				&& LocalDateTime.now().isBefore(auth.expiresAt());
	}

	/** 이메일 변경을 저장했으면 확인 기록을 지운다 (다음 변경 때 다시 확인) */
	public void clear(HttpSession session) {
		if (session != null) {
			session.removeAttribute(SESSION_ATTR);
		}
	}

	private void authorize(HttpSession session, User user) {
		session.setAttribute(SESSION_ATTR,
				new Authorization(user.getId(), LocalDateTime.now().plusMinutes(AUTH_VALID_MINUTES)));
	}

	private static IllegalStateException lockedException() {
		return new LocalizedIllegalStateException("settings.email.passwordLocked", MAX_FAILED_ATTEMPTS, LOCK_MINUTES);
	}

	private record Authorization(Long userId, LocalDateTime expiresAt) implements Serializable {
	}

	// 틀린 횟수와 잠금 해제 시각. 5회째에 잠금 시각이 정해지고, 그 시각이 지나면 처음부터 다시 센다.
	private record FailedAttempts(int count, LocalDateTime lockedUntil) {
		static FailedAttempts first() {
			return new FailedAttempts(1, null);
		}
		FailedAttempts failedOnce() {
			int next = count + 1;
			return new FailedAttempts(next, next >= MAX_FAILED_ATTEMPTS ? LocalDateTime.now().plusMinutes(LOCK_MINUTES) : null);
		}
		boolean isLocked() {
			return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
		}
		boolean isExpiredLock() {
			return lockedUntil != null && !LocalDateTime.now().isBefore(lockedUntil);
		}
	}
}
