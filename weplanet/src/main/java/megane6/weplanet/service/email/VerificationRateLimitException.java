package megane6.weplanet.service.email;

// AUTH-11: 인증코드 발송 제한(60초 재발송 / 하루 한도)에 걸렸을 때. 메시지는 화면에 그대로 보여준다.
public class VerificationRateLimitException extends RuntimeException {
	
	public VerificationRateLimitException(String message) {
		super(message);
	}
}
