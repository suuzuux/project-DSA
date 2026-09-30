package megane6.weplanet.service.email;

import megane6.weplanet.exception.LocalizedMessage;

// AUTH-11: 인증코드 발송 제한(60초 재발송 / 하루 한도)에 걸렸을 때.
// SETTINGS-03 병합: 메시지는 키 + 값({0})으로 들고 다니고, 컨트롤러가 Messages.resolve(e)로 현재 로케일 문구로 바꿔 보여준다.
public class VerificationRateLimitException extends RuntimeException implements LocalizedMessage {
	
	private final Object[] args;
	
	public VerificationRateLimitException(String messageKey, Object... args) {
		super(messageKey);
		this.args = args == null ? new Object[0] : args.clone();
	}
	
	@Override
	public String getMessageKey() {
		return getMessage();
	}
	
	@Override
	public Object[] getMessageArgs() {
		return args.clone();
	}
}
