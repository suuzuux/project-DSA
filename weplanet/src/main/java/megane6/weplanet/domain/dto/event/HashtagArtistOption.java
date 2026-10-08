package megane6.weplanet.domain.dto.event;

/** 이벤트 폼 아티스트 검색 결과·선택 줄 정보 (필드 이름이 JSON 키). */
public record HashtagArtistOption(
		Long artistId,
		String name,
		String nameEn,
		String agencyName,
		String profileImg
) {
}
