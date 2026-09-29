package megane6.weplanet.domain.dto.event;

/**
 * 해시태그 총공 순위표 한 줄 (참여 아티스트 1팀)
 * 관리자 모니터링과 공개 이벤트 페이지가 같이 쓴다.
 */
public record HashtagRankingRow(
		Long targetId,
		Long artistId,
		String artistName,
		String profileImg,
		String hashtag,
		long memberCount,		// 가입자 수 (분모)
		long participantCount,	// 인정된 글을 1건 이상 쓴 현재 가입자 수 (분자)
		long postCount,			// 인정된 글 수
		int rank
) {
	
	// 참여율(%) = 참여 인원 / 가입자 x 100, 소수 첫째 자리까지 (38.04 → 38.0)
	public double participationRate() {
		if (memberCount == 0) {
			return 0;
		}
		
		return Math.round(participantCount * 1000.0 / memberCount) / 10.0;
	}
	
	// record는 값을 바꿀 수 없어서, 순위만 바꾼 "새 줄"을 만들어 돌려준다
	public HashtagRankingRow withRank(int newRank) {
		return new HashtagRankingRow(
				targetId,
				artistId,
				artistName,
				profileImg,
				hashtag,
				memberCount,
				participantCount,
				postCount,
				newRank
		);
	}
}
