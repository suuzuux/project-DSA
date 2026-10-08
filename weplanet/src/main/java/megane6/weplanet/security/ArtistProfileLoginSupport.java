package megane6.weplanet.security;

import jakarta.servlet.http.HttpSession;

/** 그룹 로그인과 프로필 선택 사이의 대기 그룹 id (로그인 상태 아님, 프로필 선택만 가능). */
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
	
	// 대기 중인 그룹 id (없거나 만료면 null)
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
