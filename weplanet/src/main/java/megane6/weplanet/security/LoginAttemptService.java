package megane6.weplanet.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 비밀번호 대입 방어 - 일정 시간 안에 아이디·IP 별 실패가 한도를 넘으면 잠근다 (메모리 기록). */
@Slf4j
@Service
public class LoginAttemptService {

	public static final int MAX_FAILED_ATTEMPTS = 5;
	public static final long LOCK_MINUTES = 10;
	static final int IP_MAX_FAILURES = 20;
	static final long WINDOW_MINUTES = 10;

	private final Map<String, Attempts> byUsername = new ConcurrentHashMap<>();
	private final Map<String, Attempts> byIp = new ConcurrentHashMap<>();

	/** 이 아이디나 이 IP 가 지금 로그인할 수 없는 상태인지 */
	public boolean isBlocked(String username, String ip) {
		LocalDateTime now = LocalDateTime.now();
		Attempts user = byUsername.get(key(username));
		Attempts client = ip == null ? null : byIp.get(ip);
		return (user != null && user.isLocked(now)) || (client != null && client.isLocked(now));
	}

	/** 비밀번호 실패 기록 (없는 아이디 포함) */
	public void recordFailure(String username, String ip) {
		LocalDateTime now = LocalDateTime.now();
		if (username != null && !username.isBlank()) {
			Attempts updated = byUsername.compute(key(username), (k, current) ->
					current == null || current.isExpired(now) ? Attempts.first(MAX_FAILED_ATTEMPTS, now) : current.failedOnce(now));
			if (updated.count() == MAX_FAILED_ATTEMPTS) {
				log.warn("[로그인] 비밀번호 {}회 오입력으로 아이디 잠금: username={}, ip={}", MAX_FAILED_ATTEMPTS, username, ip);
			}
		}
		if (ip != null) {
			Attempts updated = byIp.compute(ip, (k, current) ->
					current == null || current.isExpired(now) ? Attempts.first(IP_MAX_FAILURES, now) : current.failedOnce(now));
			if (updated.count() == IP_MAX_FAILURES) {
				log.warn("[로그인] 같은 IP 에서 {}분 안에 {}회 실패해 IP 차단: ip={}", WINDOW_MINUTES, IP_MAX_FAILURES, ip);
			}
		}
	}

	/** 로그인 성공 시 실패 횟수 삭제 */
	public void recordSuccess(String username) {
		if (username != null) {
			byUsername.remove(key(username));
		}
	}

	/** 비밀번호 재설정 시 잠금 해제 */
	public void reset(String username) {
		recordSuccess(username);
	}

	// 만료된 기록 정리
	@Scheduled(fixedDelay = 60 * 60 * 1000L)
	public void purgeExpired() {
		LocalDateTime now = LocalDateTime.now();
		byUsername.values().removeIf(a -> a.isExpired(now));
		byIp.values().removeIf(a -> a.isExpired(now));
	}

	// 아이디는 대소문자 구분 없이 같은 키로 센다.
	private static String key(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

	/** 실패 기록 (WINDOW_MINUTES 안의 횟수만, 한도째에 잠금 시각 설정). */
	private record Attempts(int count, int limit, LocalDateTime firstFailedAt, LocalDateTime lockedUntil) {

		static Attempts first(int limit, LocalDateTime now) {
			return new Attempts(1, limit, now, limit <= 1 ? now.plusMinutes(LOCK_MINUTES) : null);
		}

		Attempts failedOnce(LocalDateTime now) {
			int next = count + 1;
			LocalDateTime lock = lockedUntil != null ? lockedUntil
					: (next >= limit ? now.plusMinutes(LOCK_MINUTES) : null);
			return new Attempts(next, limit, firstFailedAt, lock);
		}

		boolean isLocked(LocalDateTime now) {
			return lockedUntil != null && now.isBefore(lockedUntil);
		}

		boolean isExpired(LocalDateTime now) {
			if (lockedUntil != null) {
				return !now.isBefore(lockedUntil);
			}
			return now.isAfter(firstFailedAt.plusMinutes(WINDOW_MINUTES));
		}
	}
}
