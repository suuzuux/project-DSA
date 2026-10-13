package megane6.weplanet.domain.event;
import megane6.weplanet.service.fan.BadgeEventListener;
import megane6.weplanet.service.fan.PostService;

/** 배지 조건에 영향을 주는 활동 이벤트 (지급 여부는 BadgeEventListener 가 판단). */
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
		ARTIST_FOLLOWED,	// 아티스트 프로필 팔로우
		MEDIA_VIEWED,		// 미디어 상세 열람
		LIVE_VIEWED,		// 실시간 라이브 방송 시청
		PROJECT_JOINED,		// 팬 프로젝트 참여 (결제 완료)
		MEMBERSHIP_JOINED	// 멤버십 가입/갱신
	}
}
