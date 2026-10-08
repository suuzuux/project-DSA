package megane6.weplanet.exception;

/** 메시지 키와 {0}, {1} 값을 함께 담는 예외 (Messages.resolve 가 번역). */
public interface LocalizedMessage {

	/** 메시지 키 (getMessage() 와 같은 값) */
	String getMessageKey();

	/** 문구의 {0}, {1} ... 자리에 들어갈 값 */
	Object[] getMessageArgs();
}
