package megane6.weplanet.domain.dto;

/**
 * 메인/메뉴에 보여줄 아티스트 카드용 가벼운 뷰 모델.
 * profileImageUrl: 포털 프로필 로고(커뮤니티 대표 사진). 없으면 logo 이니셜 사용.
 */
public record ArtistCardView(Long id, String nickname, String logo, String profileImageUrl) {

	public static ArtistCardView from(megane6.weplanet.domain.entity.User user) {
		return from(user, null);
	}

	public static ArtistCardView from(megane6.weplanet.domain.entity.User user, String profileImageUrl) {
		String image = profileImageUrl == null || profileImageUrl.isBlank() ? null : profileImageUrl.trim();
		return new ArtistCardView(user.getId(), user.getNickname(), logoOf(user.getNickname()), image);
	}

	private static String logoOf(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			return "?";
		}
		String trimmed = nickname.trim();
		return trimmed.length() >= 2
				? trimmed.substring(0, 2).toUpperCase()
				: trimmed.toUpperCase();
	}
}
