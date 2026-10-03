package megane6.weplanet.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

// 한 계정의 로그인 세션을 모두 끊는다 (비밀번호 재설정 직후 등).
// 세션 목록은 "계정당 동시 로그인 1개"(SecurityConfig maximumSessions)가 쓰는 SessionRegistry 를 그대로 쓴다.
// 끊긴 세션은 다음 요청에서 ConcurrentSessionFilter 가 로그인 화면(/login?duplicateLogin=true)으로 보낸다.
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionExpirer {

	private final SessionRegistry sessionRegistry;

	/** @return 끊은 세션 수 */
	public int expireAllSessions(Long userId) {
		int expired = 0;
		for (Object principal : sessionRegistry.getAllPrincipals()) {
			if (principal instanceof AuthenticatedUser user && userId.equals(user.getId())) {
				for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
					session.expireNow();
					expired++;
				}
			}
		}
		if (expired > 0) {
			log.info("[세션] 계정의 로그인 세션 {}개를 끊었습니다: userId={}", expired, userId);
		}
		return expired;
	}
}
