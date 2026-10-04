package megane6.weplanet.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 아이디·비밀번호 로그인(팬·포털·관리자 공통)의 비밀번호 대입 방어. 서버 메모리에 기록한다(재시작하면 초기화).
 * {@value #WINDOW_MINUTES}분 안에 아이디는 {@value #MAX_FAILED_ATTEMPTS}회, IP 는 {@value #IP_MAX_FAILURES}회 틀리면 {@value #LOCK_MINUTES}분 동안 로그인을 막는다.
 */
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

	/** 비밀번호가 틀렸을 때 (없는 아이디 포함) */
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

	/** 로그인 성공 - 그 아이디의 실패 횟수를 지운다 */
	public void recordSuccess(String username) {
		if (username != null) {
			byUsername.remove(key(username));
		}
	}

	/** 비밀번호를 재설정했을 때 - 본인이 계정을 되찾았으니 잠금을 푼다 */
	public void reset(String username) {
		recordSuccess(username);
	}

	// 잠금이 풀렸거나 집계 시간이 지난 기록은 쌓이지 않게 정리한다
	@Scheduled(fixedDelay = 60 * 60 * 1000L)
	public void purgeExpired() {
		LocalDateTime now = LocalDateTime.now();
		byUsername.values().removeIf(a -> a.isExpired(now));
		byIp.values().removeIf(a -> a.isExpired(now));
	}

	// 아이디는 DB 에서 대소문자를 구분하지 않고 찾으므로 횟수도 같은 아이디로 묶는다
	private static String key(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * 실패 기록. 첫 실패부터 WINDOW_MINUTES 안의 횟수만 센다.
	 * 한도(limit)째 실패에 잠금 시각이 정해지고, 잠금 시각이 지나면 처음부터 다시 센다.
	 */
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
