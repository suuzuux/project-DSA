package megane6.weplanet.domain.dto.community;

/** 커뮤니티 게시글/댓글에 표시할 작성자 전용 프로필 정보. */
public record CommunityAuthorView(
		String nickname,
		String avatarUrl
) {
}
