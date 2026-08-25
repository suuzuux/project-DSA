package megane6.weplanet.domain.dto;

import java.time.LocalDate;

// 와이어프레임 10번: 메인 페이지(로그인 전) "커뮤니티 탐색" 카드 하나
// - debutDate가 없으면(NULL) 화면에서 데뷔일 자체를 안 보여줌
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
