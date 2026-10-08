package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.FanBadge;
import megane6.weplanet.i18n.Messages;

public record BadgeView (
		String badgeCode,
		String badgeName,
		String icon,
		String imageUrl,
		String description,
		boolean earned
) {
	// 배지 이미지 폴더 (획득 color, 미획득 grayscale, 파일명 동일)
	private static final String IMAGE_BASE = "/img/badges/";

	// 배지 이름·설명은 화면 언어 문구를 찾고 없으면 DB 값을 쓴다.
	public static BadgeView of (FanBadge badge, boolean earned, Messages messages) {
		return new BadgeView(
				badge.getBadgeCode(),
				messages.getOrDefault("badge." + badge.getBadgeCode() + ".name", badge.getBadgeName()),
				badge.getIcon(),
				toImageUrl(badge.getImageUrl(), earned),
				messages.getOrDefault("badge." + badge.getBadgeCode() + ".description", badge.getDescription()),
				earned
		);
	}

	// 이미지가 준비된 배지는 이미지, 아직 없으면 이모지
	public boolean hasImage() {
		return imageUrl() != null && !imageUrl().isBlank();
	}

	// 획득 여부에 맞는 폴더를 붙여 이미지 경로를 만든다.
	private static String toImageUrl(String fileName, boolean earned) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}
		return IMAGE_BASE + (earned ? "color/" : "grayscale/") + fileName;
	}
}
