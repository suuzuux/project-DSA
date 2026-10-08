package megane6.weplanet.domain.dto.event;

// 참여 아티스트 목록 한 줄 (아티스트 정보 + 해시태그)
public record HashtagEventTargetRow(
		HashtagArtistOption artist,
		String hashtag
) {
}