package megane6.weplanet.service;

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

/** 이메일 변경 전 비밀번호 확인 - 10분 유지, 5회 실패 시 10분 잠금, 소셜 전용 계정은 통과. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailChangeAuthService {

	static final long AUTH_VALID_MINUTES = 10;
	static final int MAX_FAILED_ATTEMPTS = 5;
	static final long LOCK_MINUTES = 10;

	private static final String SESSION_ATTR = "weplanet.emailChangeAuth";

	private final PasswordEncoder passwordEncoder;
	// userId → 실패 횟수·잠금 해제 시각 (메모리)
	private final Map<Long, FailedAttempts> failedAttempts = new ConcurrentHashMap<>();

	/** 비밀번호 확인이 필요한 계정인지 */
	public boolean requiresPassword(User user) {
		return user.hasPassword();
	}

	/** 비밀번호가 맞으면 이 세션에 확인 완료를 기록한다. */
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

	/** 이 세션에서 본인 확인을 마쳤는지 (소셜 전용은 항상 true) */
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

	/** 이메일 변경 후 확인 기록 삭제 */
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

	// 실패 횟수와 잠금 해제 시각 (5회째 잠금)
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
