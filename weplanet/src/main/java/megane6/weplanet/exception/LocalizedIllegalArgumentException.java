package megane6.weplanet.exception;

/** 문구 값을 함께 담는 IllegalArgumentException (400, Messages.resolve(e) 로 번역). */
public class LocalizedIllegalArgumentException extends IllegalArgumentException implements LocalizedMessage {

	private final Object[] args;

	public LocalizedIllegalArgumentException(String messageKey, Object... args) {
		// getMessage() 는 키 그대로 (번역은 Messages.resolve(e) 로 해야 {0} 이 채워짐).
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
