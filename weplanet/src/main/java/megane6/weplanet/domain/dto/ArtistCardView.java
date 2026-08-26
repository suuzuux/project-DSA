package megane6.weplanet.domain.dto;

/**
 * 메인/메뉴에 보여줄 아티스트 카드용 가벼운 뷰 모델
 */
public record ArtistCardView(Long id, String nickname, String logo) {

	public static ArtistCardView from(megane6.weplanet.domain.entity.User user) {
		return new ArtistCardView(user.getId(), user.getNickname(), logoOf(user.getNickname()));
	}

	/** Thymeleaf JS inlining / Jackson 호환용 JavaBean getter (record accessor id()만으로는 {} 로 직렬화되는 경우가 있음) */
	public Long getId() {
		return id;
	}

	public String getNickname() {
		return nickname;
	}

	public String getLogo() {
		return logo;
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
