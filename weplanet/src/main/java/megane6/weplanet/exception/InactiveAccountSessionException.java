package megane6.weplanet.exception;

// 세션은 남았는데 계정이 정지·탈퇴된 경우 (세션 정리 후 안내).
public class InactiveAccountSessionException extends StaleSessionException {
	public InactiveAccountSessionException(String message) {
		super(message);
	}
}
