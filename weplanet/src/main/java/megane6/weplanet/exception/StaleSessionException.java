package megane6.weplanet.exception;

// 로그인 세션(SecurityContext)에는 "이 유저로 로그인됨"이라고 남아있는데, 정작 그 유저가 DB에는
// 더 이상 존재하지 않는 경우 (관리자가 계정을 직접 삭제한 경우 등)에 던진다.
// 이 예외는 일반적인 "잘못된 요청"(IllegalArgumentException)과 다르게 취급해야 한다 - GlobalExceptionHandler에서
// 세션 자체를 정리(로그아웃)해주지 않으면, 이후 어떤 페이지를 가든(심지어 에러 화면의 "홈으로" 버튼조차)
// 매번 똑같은 예외가 다시 터져서 사용자가 스스로는 빠져나올 방법이 없는 상태에 갇히게 된다.
public class StaleSessionException extends RuntimeException {
	public StaleSessionException(String message) {
		super(message);
	}
}
