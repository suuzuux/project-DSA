package megane6.weplanet.domain.dto;

import java.util.List;

public record CollectionCardView(
		Long artistId,
		String artistNickname,
		long basicCount,
		long specialCount,
		int achievementRate,
		List<BadgeView> previewBadges
) {
}