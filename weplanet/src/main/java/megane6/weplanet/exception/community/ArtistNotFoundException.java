package megane6.weplanet.exception.community;

public class ArtistNotFoundException extends RuntimeException {
	public ArtistNotFoundException() {
		super("아티스트를 찾을 수 없습니다.");
	}
}