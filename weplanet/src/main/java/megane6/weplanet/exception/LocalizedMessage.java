package megane6.weplanet.exception;

/**
 * 메시지 키와 문구에 끼워 넣을 값({0}, {1} ...)을 함께 들고 다니는 예외 - Messages.resolve(Throwable) 가 키 + 값으로 번역한다.
 * 쉼표가 들어가면 안 되는 숫자(ID·연도)는 String.valueOf 로 넘기고, 값이 들어가는 문구의 작은따옴표는 '' 로 쓴다.
 */
public interface LocalizedMessage {

	/** 메시지 키 (getMessage() 와 같은 값) */
	String getMessageKey();

	/** 문구의 {0}, {1} ... 자리에 들어갈 값 */
	Object[] getMessageArgs();
}
