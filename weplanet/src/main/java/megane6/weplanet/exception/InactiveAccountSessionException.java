package megane6.weplanet.exception;

// 로그인 세션은 남아 있는데 그 사이 계정이 정지·탈퇴 등으로 사용할 수 없게 된 경우.
// 세션을 정리(로그아웃)하고 로그인 화면에 "이용할 수 없는 계정" 안내를 띄운다.
public class InactiveAccountSessionException extends StaleSessionException {
	public InactiveAccountSessionException(String message) {
		super(message);
	}
}
