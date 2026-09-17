package megane6.weplanet.domain.entity.enumfolder;

/**
 * fan_badge.badge_code 값 목록.
 * <p>
 * 코드에서 "BASIC_FIRST_JOIN" 같은 문자열을 직접 쓰면 오타가 나도 컴파일러가 못 잡아준다.
 * enum 으로 모아두면 오타는 컴파일 에러가 되고, 어떤 배지가 있는지 한눈에 보인다.
 * name() 값이 그대로 DB 의 badge_code 이므로 이름을 바꾸면 안 된다.
 */
public enum BadgeCode {
	
	// ---------- 일반 배지 ----------
	BASIC_FIRST_JOIN,
	BASIC_FIRST_POST,
	BASIC_COMMENT_5,
	BASIC_MEDIA_VIEW,
	BASIC_DAY_100,
	BASIC_DAY_200,
	BASIC_DAY_300,
	BASIC_LIKE_10,
	BASIC_LIKED_5,
	BASIC_FOLLOW_ARTIST,
	BASIC_SHOP_PURCHASE,
	BASIC_LIVE_VIEW,
	BASIC_YEAR_1,
	BASIC_YEAR_2,
	BASIC_YEAR_3,
	
	// ---------- 스페셜 배지 ----------
	SPECIAL_DEBUT_1,
	SPECIAL_DEBUT_2,
	SPECIAL_DEBUT_3,
	SPECIAL_FOLLOWER_10,
	SPECIAL_MEMBERSHIP_1,
	SPECIAL_MEMBERSHIP_2,
	SPECIAL_MEMBERSHIP_3,
	SPECIAL_MEMBERSHIP_4,
	SPECIAL_MEMBERSHIP_5,
	SPECIAL_PROJECT_CREATE
}