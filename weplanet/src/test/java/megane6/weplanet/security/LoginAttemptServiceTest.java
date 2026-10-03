package megane6.weplanet.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

	private final LoginAttemptService service = new LoginAttemptService();

	@Test
	void usernameIsLockedAfterFiveFailures() {
		for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS - 1; i++) {
			service.recordFailure("fan01", "10.0.0." + i); // IP 를 바꿔도 아이디 기준으로 센다
		}
		assertFalse(service.isBlocked("fan01", "10.0.0.99"));

		service.recordFailure("FAN01", "10.0.0.50"); // 대소문자가 달라도 같은 아이디
		assertTrue(service.isBlocked("fan01", "10.0.0.99"));
		assertFalse(service.isBlocked("other", "10.0.0.99"));
	}

	@Test
	void successfulLoginClearsTheCount() {
		for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS - 1; i++) {
			service.recordFailure("fan01", "10.0.0.1");
		}
		service.recordSuccess("fan01");
		service.recordFailure("fan01", "10.0.0.1");

		assertFalse(service.isBlocked("fan01", "10.0.0.1"));
	}

	// 아이디를 바꿔 가며 대입해도 같은 IP 에서 20회 틀리면 그 IP 를 막는다
	@Test
	void ipIsBlockedAfterTwentyFailuresAcrossUsernames() {
		for (int i = 0; i < LoginAttemptService.IP_MAX_FAILURES - 1; i++) {
			service.recordFailure("user" + i, "10.0.0.7");
		}
		assertFalse(service.isBlocked("newcomer", "10.0.0.7"));

		service.recordFailure(null, "10.0.0.7"); // 없는 아이디는 IP 만 센다
		assertTrue(service.isBlocked("newcomer", "10.0.0.7"));
		assertFalse(service.isBlocked("newcomer", "10.0.0.8"));
	}

	@Test
	void resetUnlocksTheAccount() {
		for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS; i++) {
			service.recordFailure("fan01", "10.0.0.1");
		}
		service.reset("fan01");

		assertFalse(service.isBlocked("fan01", "10.0.0.2"));
	}
}
