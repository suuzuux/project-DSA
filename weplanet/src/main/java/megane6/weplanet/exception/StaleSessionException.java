package megane6.weplanet.exception;

// 세션의 유저가 DB 에서 사라진 경우 (GlobalExceptionHandler 가 세션 정리).
public class StaleSessionException extends RuntimeException {
	public StaleSessionException(String message) {
		super(message);
	}
}
