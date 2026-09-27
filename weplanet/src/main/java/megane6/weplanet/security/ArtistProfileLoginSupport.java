package megane6.weplanet.security;

import jakarta.servlet.http.HttpSession;

/**
 * 그룹 로그인(1단계)과 프로필 선택(2단계) 사이에 "어느 그룹의 프로필을 고르는 중인지"를 세션에 잠깐 들고 있는다.
 * 이 상태는 로그인이 아니다 - SecurityContext 는 비어 있고, 이 값으로는 /portal/profiles 화면만 열 수 있다.
 * 그룹 비밀번호만 알고 프로필을 고르지 않은 채 그룹 이름으로 활동하는 것을 막기 위함.
 */
public class ArtistProfileLoginSupport {
	
	private static final String PENDING_GROUP_ID = "artistProfileLogin.groupId";
	private static final String PENDING_EXPIRES_AT = "artistProfileLogin.expiresAt";
	
	// 그룹 로그인 후 프로필을 고를 수 있는 시간
	private static final long VALID_MILLIS = 10 * 60 * 1000L;
	
	private ArtistProfileLoginSupport() {}
	
	public static void begin(HttpSession session,Long groupId) {
		session.setAttribute(PENDING_GROUP_ID, groupId);
		session.setAttribute(PENDING_EXPIRES_AT, System.currentTimeMillis() + VALID_MILLIS);
	}
	
	// 대기 중인 그룹id. 없거나 시간이 지났으면 null
	public static Long pendingGroupId(HttpSession session) {
		if (session == null) {
			return null;
		}
		
		Object groupId = session.getAttribute(PENDING_GROUP_ID);
		Object expiresAt =  session.getAttribute(PENDING_EXPIRES_AT);
		
		if (!(groupId instanceof Long id) || !(expiresAt instanceof Long until)) {
			return null;
		}
		
		if (System.currentTimeMillis() > until) {
			clear(session);
			return null;
		}
		
		return id;
	}
	
	public static void clear(HttpSession session) {
		if (session == null) {
			return;
		}
		
		session.removeAttribute(PENDING_GROUP_ID);
		session.removeAttribute(PENDING_EXPIRES_AT);
	}
}
