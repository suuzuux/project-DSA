package megane6.weplanet.security;

import megane6.weplanet.domain.entity.enumfolder.AuthProvider;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 가입 확인 전까지 보관하는 소셜 정보 (카카오·LINE 은 시스템 주소). */
public record PendingSocialSignup(AuthProvider provider, String providerId, String email,
								  String realName, String suggestedNickname, LocalDateTime expiresAt)
		implements Serializable {

	// 확인 화면 유효 시간
	public static final long VALID_MINUTES = 10;

	public static PendingSocialSignup of(AuthProvider provider, String providerId, String email,
										 String realName, String suggestedNickname) {
		return new PendingSocialSignup(provider, providerId, email, realName, suggestedNickname,
				LocalDateTime.now().plusMinutes(VALID_MINUTES));
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}

	/** 화면에 보여줄 실제 이메일이 있는지 */
	public boolean hasRealEmail() {
		return email != null && !email.toLowerCase().endsWith(".weplanet.local");
	}
}
