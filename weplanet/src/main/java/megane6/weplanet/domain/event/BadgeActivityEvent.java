package megane6.weplanet.domain.event;

/**
 * "배지 조건에 영향을 줄 수 있는 활동이 일어났다"는 알림.
 * 활동을 한 서비스(글쓰기, 댓글 등)는 이 이벤트만 던지고 끝낸다.
 * 배지를 줄지 말지는 BadgeEventListener 가 판단한다.
 * → PostService 같은 기존 서비스가 배지 규칙을 몰라도 된다.
 *
 * @param fanId    배지를 받을 수 있는 사람 (활동한 사람, 또는 좋아요를 받은 글쓴이)
 * @param artistId 어느 커뮤니티에서 일어난 활동인지
 * @param activity 어떤 활동인지
 */
public record BadgeActivityEvent(
		Long fanId,
		Long artistId,
		Activity activity
) {
	public enum Activity {
		COMMUNITY_JOINED,	// 커뮤니티 가입
		POST_CREATED,		// 팬 게시판 글 작성
		COMMENT_CREATED,	// 댓글 작성
		LIKE_GIVEN,			// 좋아요 누름 (누른 사람 기준)
		LIKE_RECEIVED,		// 좋아요 받음 (글쓴이 기준)
		ARTIST_FOLLOWED		// 아티스트 프로필 팔로우
	}
}
