package megane6.weplanet.domain.dto;

/** 아티스트 카드 뷰 (profileImageUrl 이 없으면 logo 이니셜, homeUrl 은 영문 주소 우선). */
public record ArtistCardView(Long id, String nickname, String logo, String profileImageUrl, String homeUrl) {

	public static ArtistCardView from(megane6.weplanet.domain.entity.User user) {
		return from(user, null);
	}

	public static ArtistCardView from(megane6.weplanet.domain.entity.User user, String profileImageUrl) {
		String image = profileImageUrl == null || profileImageUrl.isBlank() ? null : profileImageUrl.trim();
		return new ArtistCardView(user.getId(), user.getNickname(), logoOf(user.getNickname()), image,
				"/community/" + user.getId());
	}

	public ArtistCardView withHomeUrl(String homeUrl) {
		return new ArtistCardView(id, nickname, logo, profileImageUrl, homeUrl);
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
