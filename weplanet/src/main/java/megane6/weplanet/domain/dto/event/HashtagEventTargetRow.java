package megane6.weplanet.domain.dto.event;

// 폼을 (다시) 그릴 때 "참여 아티스트" 목록의 한 줄: 아티스트 정보 + 입력된 해시태그
public record HashtagEventTargetRow(
		HashtagArtistOption artist,
		String hashtag
) {
}