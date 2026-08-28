package megane6.weplanet.domain.dto.community;

import megane6.weplanet.domain.entity.enumfolder.GroupGender;

import java.time.LocalDate;

public record ArtistSearchRow(
		Long artistId,
		String nickname,
		GroupGender gender,
		Integer memberCount,
		String nationality,
		String category,
		LocalDate debutDate
) {
}