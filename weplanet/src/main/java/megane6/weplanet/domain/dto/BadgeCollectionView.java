package megane6.weplanet.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BadgeCollectionView(
		Long artistId,
		String artistNickname,
		List<BadgeView> basicBadges,
		List<BadgeView> specialBadges
) {
	// 전체 배지 수 (달성률의 분모)
	@JsonProperty
	public int totalCount() {
		return basicBadges.size() + specialBadges.size();
	}
	
	// 획득한 배지 수 (달성률의 분자)
	@JsonProperty
	public long earnedCount() {
		return basicBadges.stream().filter(BadgeView::earned).count()
				+ specialBadges.stream().filter(BadgeView::earned).count();
	}
	
	// 달성률(%)
	@JsonProperty
	public int achievementRate() {
		int total = totalCount();
		if (total == 0) {
			return 0;
		}
		return (int) (earnedCount() * 100 / total);
	}
}
