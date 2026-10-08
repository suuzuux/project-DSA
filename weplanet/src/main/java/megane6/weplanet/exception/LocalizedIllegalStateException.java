package megane6.weplanet.exception;

/** 문구 값을 함께 담는 IllegalStateException (403, Messages.resolve(e) 로 번역). */
public class LocalizedIllegalStateException extends IllegalStateException implements LocalizedMessage {

	private final Object[] args;

	public LocalizedIllegalStateException(String messageKey, Object... args) {
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
