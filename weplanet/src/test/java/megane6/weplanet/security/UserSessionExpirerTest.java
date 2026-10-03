package megane6.weplanet.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistryImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSessionExpirerTest {

	// 비밀번호를 재설정한 계정의 세션만 끊고, 다른 계정의 세션은 그대로 둔다
	@Test
	void expiresOnlyTheTargetUsersSessions() {
		SessionRegistryImpl registry = new SessionRegistryImpl();
		registry.registerNewSession("target-session", principal(1L, "victim"));
		registry.registerNewSession("other-session", principal(2L, "someone"));

		int expired = new UserSessionExpirer(registry).expireAllSessions(1L);

		assertEquals(1, expired);
		assertTrue(registry.getSessionInformation("target-session").isExpired());
		assertFalse(registry.getSessionInformation("other-session").isExpired());
	}

	private static AuthenticatedUser principal(Long id, String username) {
		return AuthenticatedUser.builder()
				.id(id)
				.username(username)
				.password("encoded")
				.nickname(username)
				.roleName("ROLE_FAN")
				.enabled(true)
				.build();
	}
}
