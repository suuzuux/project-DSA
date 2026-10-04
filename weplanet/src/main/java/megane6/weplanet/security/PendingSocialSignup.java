package megane6.weplanet.security;

import megane6.weplanet.domain.entity.enumfolder.AuthProvider;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 소셜 인증은 끝났지만 위플래닛 계정이 없는 사람의 소셜 정보 - "가입하시겠습니까?" 확인 전까지 세션에 담아 둔다.
 * email 은 구글이면 실제 이메일, 카카오/LINE 이면 받을 수 없는 시스템 주소(*.weplanet.local)다.
 */
public record PendingSocialSignup(AuthProvider provider, String providerId, String email,
								  String realName, String suggestedNickname, LocalDateTime expiresAt)
		implements Serializable {

	// 확인 화면에서 너무 오래 머물면 다시 소셜 로그인부터 하게 한다
	public static final long VALID_MINUTES = 10;

	public static PendingSocialSignup of(AuthProvider provider, String providerId, String email,
										 String realName, String suggestedNickname) {
		return new PendingSocialSignup(provider, providerId, email, realName, suggestedNickname,
				LocalDateTime.now().plusMinutes(VALID_MINUTES));
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}

	/** 화면에 보여줄 이메일 - 카카오/LINE 의 시스템 주소는 보여주지 않는다 */
	public boolean hasRealEmail() {
		return email != null && !email.toLowerCase().endsWith(".weplanet.local");
	}
}
