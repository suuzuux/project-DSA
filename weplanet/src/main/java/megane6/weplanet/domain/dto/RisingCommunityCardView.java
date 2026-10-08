package megane6.weplanet.domain.dto;

import java.time.LocalDate;

// 메인 커뮤니티 탐색 카드 (데뷔일이 없으면 표시하지 않음).
public record RisingCommunityCardView(Long id, String nickname, String logo, LocalDate debutDate, long followerCount) {

	public static RisingCommunityCardView of(megane6.weplanet.domain.entity.User user, LocalDate debutDate, long followerCount) {
		String nickname = user.getNickname();
		String logo;
		if (nickname == null || nickname.isBlank()) {
			logo = "?";
		} else {
			String trimmed = nickname.trim();
			logo = trimmed.length() >= 2 ? trimmed.substring(0, 2).toUpperCase() : trimmed.toUpperCase();
		}
		return new RisingCommunityCardView(user.getId(), nickname, logo, debutDate, followerCount);
	}
}
