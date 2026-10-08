package megane6.weplanet.service.email;

import megane6.weplanet.exception.LocalizedMessage;

// 인증코드 발송 제한에 걸렸을 때 (메시지 키 + 값).
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
