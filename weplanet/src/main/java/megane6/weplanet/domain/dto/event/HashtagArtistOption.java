package megane6.weplanet.domain.dto.event;

/**
 * 이벤트 폼의 아티스트 검색 결과 1줄 / 선택된 참여 아티스트 1줄에 보여줄 정보.
 * 검색은 fetch 로 JSON 을 받아 그리므로, 필드 이름이 그대로 JSON 키가 된다.
 */
public record HashtagArtistOption(
		Long artistId,
		String name,
		String nameEn,
		String agencyName,
		String profileImg
) {
}
