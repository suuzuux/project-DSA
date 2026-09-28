package megane6.weplanet.domain.dto;

/**
 * 메인/메뉴에 보여줄 아티스트 카드용 가벼운 뷰 모델.
 * profileImageUrl: 포털 프로필 로고(커뮤니티 대표 사진). 없으면 logo 이니셜 사용.
 * homeUrl: 커뮤니티 첫 화면 주소. 영문 주소(/kiikii)가 있으면 그걸, 없으면 /community/{id}.
 *          from()은 DB를 보지 않으므로 숫자 주소로 만들고, 영문 주소는 CommunityUrls가 withHomeUrl로 채운다.
 */
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
