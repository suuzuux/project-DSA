package megane6.weplanet.exception;

// 로그인이 꼭 필요한 동작(글쓰기, 댓글, 좋아요 등)에 비로그인 상태로 접근했을 때 던짐.
// 예전엔 testUserId 파라미터로 "누구 명의로 할지"를 대신 정할 수 있었는데(개발 편의용),
// 그걸 로그인 없이도 그대로 쓸 수 있어서 남의 계정 명의로 글이 써지는 문제가 있었음 - 이제 이 동작들은 실제 로그인을 요구함
public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException() {
        super("로그인이 필요합니다.");
    }
}
