package megane6.weplanet.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 설정 화면 "이메일 변경" 전 본인 확인 (현재 비밀번호 재확인).
 * <p>
 * 예전에는 아래쪽 "현재 비밀번호" 칸 하나를 비밀번호 변경과 이메일 변경 확인에 같이 써서, 이메일만 바꾸려는 사람에게도
 * 새 비밀번호 칸이 열려 헷갈렸다. 이제 이메일 옆 "수정하기"를 누르면 먼저 현재 비밀번호를 확인하고, 맞으면 이 세션에
 * "이메일 변경 본인 확인 완료"를 10분 동안 기록한다. 인증코드 발송(/settings/email/code)과 최종 저장(/settings/profile)은
 * 이 기록이 있어야만 통과한다 - 화면(JS)에서 이메일 칸만 열어 주는 것으로는 우회할 수 없게 서버에서 확인한다.
 * <p>
 * 비밀번호가 없는 소셜 전용 계정은 확인할 비밀번호가 없으므로 항상 확인된 것으로 본다 (새 이메일 인증만 거친다).
 * 비밀번호를 5회 틀리면 10분 동안 확인할 수 없다 (로그인된 브라우저를 잠깐 쓴 사람이 비밀번호를 대입해 보는 것 방지).
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
	 * @throws IllegalArgumentException 비밀번호가 비었거나 틀린 경우 (화면에 그대로 보여줄 문구)
	 * @throws IllegalStateException    5회 오입력으로 잠긴 경우
	 */
	public void confirmPassword(HttpSession session, User user, String password) {
		if (!requiresPassword(user)) {
			authorize(session, user);
			return;
		}
		FailedAttempts attempts = failedAttempts.get(user.getId());
		if (attempts != null && attempts.isLocked()) {
			throw new IllegalStateException(lockedMessage());
		}
		if (password == null || password.isBlank()) {
			throw new IllegalArgumentException("현재 비밀번호를 입력해주세요.");
		}
		if (!passwordEncoder.matches(password, user.getPassword())) {
			FailedAttempts updated = failedAttempts.compute(user.getId(), (id, current) ->
					(current == null || current.isExpiredLock()) ? FailedAttempts.first() : current.failedOnce());
			if (updated.isLocked()) {
				log.warn("[이메일 변경] 현재 비밀번호 {}회 오입력으로 잠금: userId={}", MAX_FAILED_ATTEMPTS, user.getId());
				throw new IllegalStateException(lockedMessage());
			}
			throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다. (남은 시도 "
					+ (MAX_FAILED_ATTEMPTS - updated.count()) + "회)");
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

	private static String lockedMessage() {
		return "현재 비밀번호를 " + MAX_FAILED_ATTEMPTS + "회 잘못 입력해서 " + LOCK_MINUTES
				+ "분 동안 이메일을 변경할 수 없습니다. 잠시 후 다시 시도해주세요.";
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
