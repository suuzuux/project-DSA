package megane6.weplanet.domain.event;

/** 가입 완료·광고 동의 안내 메일 이벤트 (커밋 뒤 백그라운드 발송). */
public record AccountMailEvent(Long userId, Kind kind, boolean marketingConsentGiven) {

	public enum Kind {
		// 가입 완료 메일 (광고 동의 시 커뮤니티 가입 유도 메일도 보냄)
		SIGNUP_WELCOME,
		// 광고성 정보 알림을 켰을 때의 동의 확인 메일
		MARKETING_CONSENT_CONFIRMED
	}

	public static AccountMailEvent signupWelcome(Long userId, boolean marketingConsentGiven) {
		return new AccountMailEvent(userId, Kind.SIGNUP_WELCOME, marketingConsentGiven);
	}

	public static AccountMailEvent marketingConsentConfirmed(Long userId) {
		return new AccountMailEvent(userId, Kind.MARKETING_CONSENT_CONFIRMED, true);
	}
}
