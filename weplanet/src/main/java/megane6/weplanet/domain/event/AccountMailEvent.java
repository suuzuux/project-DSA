package megane6.weplanet.domain.event;

/**
 * 가입 완료·광고성 정보 동의 안내 메일을 보내 달라는 이벤트 (AccountMailListener 가 받는다).
 * 가입·설정 저장이 DB 에 확정된 뒤 백그라운드로 보내서, 화면이 메일 발송을 기다리지 않는다.
 *
 * @param marketingConsentGiven 가입할 때 "(선택) 광고 및 마케팅 활용 동의"를 했는지 (SIGNUP_WELCOME 에서만 씀)
 */
public record AccountMailEvent(Long userId, Kind kind, boolean marketingConsentGiven) {

	public enum Kind {
		// 가입 완료 메일. 광고·마케팅에 동의했으면 커뮤니티 가입 유도 메일 1통을 이어서 보낸다
		SIGNUP_WELCOME,
		// 설정 화면에서 "광고성 정보 알림 받기"를 켰을 때 보내는 동의 확인 메일
		MARKETING_CONSENT_CONFIRMED
	}

	public static AccountMailEvent signupWelcome(Long userId, boolean marketingConsentGiven) {
		return new AccountMailEvent(userId, Kind.SIGNUP_WELCOME, marketingConsentGiven);
	}

	public static AccountMailEvent marketingConsentConfirmed(Long userId) {
		return new AccountMailEvent(userId, Kind.MARKETING_CONSENT_CONFIRMED, true);
	}
}
