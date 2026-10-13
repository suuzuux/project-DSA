package megane6.weplanet.domain.dto.community;

import megane6.weplanet.domain.entity.enumfolder.GroupGender;

import java.time.LocalDate;

// 검색 결과 카드 (가입 여부는 화면이 가입 목록으로 판단).
public record ArtistSearchResultView(
		Long artistId,
		String nickname,
		String logo,
		GroupGender gender,
		Integer memberCount,
		boolean solo,
		String nationality,
		String category,
		LocalDate debutDate,
		boolean own		// 로그인한 사람의 본인 커뮤니티인지 (가입 버튼 숨김용)
) {
	public static ArtistSearchResultView of(ArtistSearchRow row) {
		boolean solo = row.memberCount() != null && row.memberCount() == 1;
		return new ArtistSearchResultView(
				row.artistId(), row.nickname(), logoOf(row.nickname()),
				row.gender(), row.memberCount(), solo, row.nationality(), row.category(),
				row.debutDate(), false
		);
	}
	
	public ArtistSearchResultView withOwn(boolean own) {
		return new ArtistSearchResultView(artistId, nickname, logo, gender, memberCount, solo,
				nationality, category, debutDate, own);
	}
	
	private static String logoOf(String nickname) {
		if (nickname == null || nickname.isBlank()) return "?";
		String trimmed = nickname.trim();
		return trimmed.length() >= 2 ? trimmed.substring(0, 2).toUpperCase() : trimmed.toUpperCase();
	}
}