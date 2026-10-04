package megane6.weplanet.domain.event;

/**
 * 가입 완료·광고성 정보 동의 같은 계정 안내 메일을 보내 달라는 이벤트 (AccountMailListener 가 받는다).
 * 메일은 가입(또는 설정 저장)이 DB 에 확정된 뒤 백그라운드에서 보낸다. 예전에는 가입 처리 안에서 바로 보내서
 * "가입하기"를 누른 뒤 메일 1~2통이 다 나갈 때까지(통당 1~2초, 메일 서버 장애 시 최대 5초) 화면이 기다렸고,
 * 그동안 DB 연결도 붙잡고 있었다.
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
