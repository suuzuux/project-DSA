package megane6.weplanet.exception;

// 로그인 세션에는 남아 있는데 그 유저가 DB 에서 사라진 경우(관리자가 계정을 삭제 등)에 던진다.
// GlobalExceptionHandler 가 세션을 정리(로그아웃)한다 - 안 그러면 어느 페이지에서든 같은 예외가 반복된다.
public class StaleSessionException extends RuntimeException {
	public StaleSessionException(String message) {
		super(message);
	}
}
