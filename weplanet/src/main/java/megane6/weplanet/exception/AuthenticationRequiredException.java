package megane6.weplanet.exception;

// 로그인이 필요한 동작에 비로그인으로 접근했을 때 던진다.
public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException() {
        super("common.error.loginRequired");
    }
}
