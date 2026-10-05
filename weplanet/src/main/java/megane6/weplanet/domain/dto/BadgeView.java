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
	// 배지 이미지 폴더. 획득 = color, 미획득 = grayscale (두 폴더의 파일명은 같다)
	private static final String IMAGE_BASE = "/img/badges/";

	// 이름·설명은 배지 코드로 화면 언어 문구(badge.{코드}.name / .description)를 찾고, 없으면 DB 값(한국어)을 쓴다
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

	// DB에는 파일명만 저장돼 있으므로, 획득 여부에 맞는 폴더를 붙여 실제 경로로 만든다.
	private static String toImageUrl(String fileName, boolean earned) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}
		return IMAGE_BASE + (earned ? "color/" : "grayscale/") + fileName;
	}
}
