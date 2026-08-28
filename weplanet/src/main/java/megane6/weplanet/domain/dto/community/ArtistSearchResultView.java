package megane6.weplanet.domain.dto.community;

import megane6.weplanet.domain.entity.enumfolder.GroupGender;

import java.time.LocalDate;

// 검색 결과 카드 하나 - 로고/솔로 여부까지 계산해서 화면에 그대로 뿌릴 수 있게 함.
// "가입했는지 여부(joined)"는 EXPLORE-03(CommunityMember)이 생기면 다시 추가할 예정.
public record ArtistSearchResultView(
		Long artistId,
		String nickname,
		String logo,
		GroupGender gender,
		Integer memberCount,
		boolean solo,
		String nationality,
		String category,
		LocalDate debutDate
) {
	public static ArtistSearchResultView of(ArtistSearchRow row) {
		boolean solo = row.memberCount() != null && row.memberCount() == 1;
		return new ArtistSearchResultView(
				row.artistId(), row.nickname(), logoOf(row.nickname()),
				row.gender(), row.memberCount(), solo, row.nationality(), row.category(),
				row.debutDate()
		);
	}
	
	private static String logoOf(String nickname) {
		if (nickname == null || nickname.isBlank()) return "?";
		String trimmed = nickname.trim();
		return trimmed.length() >= 2 ? trimmed.substring(0, 2).toUpperCase() : trimmed.toUpperCase();
	}
}