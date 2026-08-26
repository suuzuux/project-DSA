package megane6.weplanet.domain.dto;

// 와이어프레임 26번: About 위젯에 뜨는 "다른 아티스트 팔로우" 카드 하나
// (팔로우 버튼 상태까지 같이 담아서, 화면에서는 이 값만 뿌려주면 되게 함)
public record ArtistFollowCardView(Long id, String nickname, String logo, boolean following) {

	public static ArtistFollowCardView of(megane6.weplanet.domain.entity.User user, boolean following) {
		String nickname = user.getNickname();
		String logo;
		if (nickname == null || nickname.isBlank()) {
			logo = "?";
		} else {
			String trimmed = nickname.trim();
			logo = trimmed.length() >= 2 ? trimmed.substring(0, 2).toUpperCase() : trimmed.toUpperCase();
		}
		return new ArtistFollowCardView(user.getId(), nickname, logo, following);
	}
}
