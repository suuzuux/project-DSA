package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.FanBadge;

public record BadgeView (
		String badgeCode,
		String badgeName,
		String icon,
		String imageUrl,
		String description,
		boolean earned
) {
	public static BadgeView of (FanBadge badge, boolean earned) {
		return new BadgeView(
				badge.getBadgeCode(),
				badge.getBadgeName(),
				badge.getIcon(),
				badge.getImageUrl(),
				badge.getDescription(),
				earned
		);
	}
	
	// 이미지가 준비된 배지는 이미지, 아직 없으면 이모지
	public boolean hasImage() {
		return imageUrl() != null && !imageUrl().isBlank();
	}
}