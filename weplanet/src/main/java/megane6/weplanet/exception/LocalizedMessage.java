package megane6.weplanet.exception;

/**
 * 메시지 키와 함께 문구에 끼워 넣을 값({0}, {1} ...)을 들고 다니는 예외.
 * <p>
 * SETTINGS-03 규칙은 "예외에는 메시지 키를 담고, 화면에 내보내는 쪽에서 번역한다"인데,
 * 예외 메시지(getMessage)는 문자열 하나라서 "이미 사용 중인 이메일입니다: {0}" 의 이메일이나
 * "{0}자 이내" 의 글자 수 같은 값을 실을 수 없었다. 이 인터페이스를 구현한 예외는 값을 따로 들고 있고,
 * Messages.resolve(Throwable) 가 키 + 값으로 번역한다.
 * <p>
 * 숫자를 넘기면 MessageFormat 이 1,234 처럼 쉼표를 넣으므로, ID 나 연도처럼 쉼표가 들어가면 안 되는 값은
 * String.valueOf(...) 로 바꿔서 넘긴다. 값이 들어가는 문구의 작은따옴표는 '' 로 써야 한다.
 */
public interface LocalizedMessage {

	/** 메시지 키 (getMessage() 와 같은 값) */
	String getMessageKey();

	/** 문구의 {0}, {1} ... 자리에 들어갈 값 */
	Object[] getMessageArgs();
}
