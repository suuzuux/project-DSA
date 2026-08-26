package megane6.weplanet.domain.dto.community;

import megane6.weplanet.domain.entity.enumfolder.GroupGender;

import java.time.LocalDate;

public record ArtistSearchRow(
		Long id,
		String nickname,
		GroupGender gender,
		String nationality,
		String category,
		Integer memberCount,
		LocalDate debutDate
) {
}