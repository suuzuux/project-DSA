package megane6.weplanet.exception;

/**
 * 값을 같이 들고 다니는 IllegalArgumentException (잘못된 입력 → 400).
 * 기존 catch (IllegalArgumentException e) 에서 그대로 잡히고, 번역은 Messages.resolve(e) 로 한다.
 * 예) throw new LocalizedIllegalArgumentException("error.banner.titleTooLong", TITLE_MAX);
 */
public class LocalizedIllegalArgumentException extends IllegalArgumentException implements LocalizedMessage {

	private final Object[] args;

	public LocalizedIllegalArgumentException(String messageKey, Object... args) {
		// getMessage() 는 키 그대로 (로그에도 키가 찍힘). 번역은 반드시 Messages.resolve(e) 로 - resolve(e.getMessage()) 로 하면 {0} 자리가 비어서 나온다
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
