package megane6.weplanet.exception;

// AUTH-11: 로그인 세션은 남아 있는데 그 사이 계정이 정지·탈퇴 등으로 "사용할 수 없는 상태"가 된 경우.
// 예전에는 관리자가 계정을 정지해도 이미 로그인해 있던 세션은 끝날 때까지 글쓰기·결제 등을 계속할 수 있었다.
// StaleSessionException 과 똑같이 세션을 정리(로그아웃)하되, 로그인 화면에 "이용할 수 없는 계정" 안내를 띄운다.
public class InactiveAccountSessionException extends StaleSessionException {
	public InactiveAccountSessionException(String message) {
		super(message);
	}
}
