package megane6.weplanet.domain.dto.community;

import megane6.weplanet.domain.entity.enumfolder.GroupGender;

public record ArtistSearchResultView(
		Long id,
		String nickname,
		String logo,
		GroupGender gender,
		String genderLabel,
		String nationality,
		String category,
		Integer memberCount,
		java.time.LocalDate debutDate,
		boolean joined
) {
	public static ArtistSearchResultView from(ArtistSearchRow row, boolean joined) {
		GroupGender g = row.gender();
		return new ArtistSearchResultView(
				row.id(), row.nickname(), logoOf(row.nickname()),
				g, g != null ? g.label() : null,
				row.nationality(), row.category(), row.memberCount(), row.debutDate(),
				joined
		);
	}
	
	private static String logoOf(String nickname) {
		if (nickname == null || nickname.isBlank()) return "?";
		String trimmed = nickname.trim();
		return trimmed.length() >= 2 ? trimmed.substring(0, 2).toUpperCase() : trimmed.toUpperCase();
	}
}