package megane6.weplanet.exception.community;

public class CommunityNotJoinedException extends RuntimeException {
	public CommunityNotJoinedException() {
		super("먼저 커뮤니티에 가입해주세요.");
	}
}