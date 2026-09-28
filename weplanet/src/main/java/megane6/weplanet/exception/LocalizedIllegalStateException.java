package megane6.weplanet.exception;

/**
 * 값을 같이 들고 다니는 IllegalStateException (상태/중복 위반 → 403).
 * 기존 catch (IllegalStateException e) 에서 그대로 잡히고, 번역은 Messages.resolve(e) 로 한다.
 * 예) throw new LocalizedIllegalStateException("error.artistRegistration.emailTaken", email);
 */
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
