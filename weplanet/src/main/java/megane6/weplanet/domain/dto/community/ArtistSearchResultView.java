package megane6.weplanet.domain.dto.community;

import megane6.weplanet.domain.entity.enumfolder.GroupGender;

import java.time.LocalDate;

// 검색 결과 카드 하나 - 로고/솔로 여부까지 계산해서 화면에 그대로 뿌릴 수 있게 함.
// "가입했는지 여부"는 여기 담지 않는다 - 화면(community-explore.js)이 드로어용으로 이미 받은 가입 커뮤니티 목록으로 판단한다.
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
		boolean own		// AUTH-11: 로그인한 사람의 "본인 커뮤니티"인지 (아티스트/그룹 멤버) - 화면에서 가입 버튼을 숨기는 데 씀
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