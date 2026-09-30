package megane6.weplanet.domain.dto.event;

import java.time.format.DateTimeFormatter;

/**
 * 집계 확정된 해시태그 총공의 "결과 공지" 초안 (제목 + 마크다운 본문).
 * 관리자 모니터링의 [결과 공지 작성] → 공지 글쓰기 폼에 미리 채워진다. 관리자가 자유롭게 고쳐서 등록하면 된다.
 */
public record HashtagResultNoticeDraft(
		String title,
		String content
) {
	private static final DateTimeFormatter DATE =
			DateTimeFormatter.ofPattern("yyyy.MM.dd");
	
	public static HashtagResultNoticeDraft from(HashtagEventDashboard d) {
		StringBuilder md = new StringBuilder();
		
		md.append(d.title()).append("에 참여해주신 모든 팬 여러분, 감사합니다! 💜\n\n");
		md.append("- 기간: ").append(d.startAt().format(DATE)).append(" ~ ").append(d.endAt().format(DATE)).append("\n");
		md.append("- 참여 커뮤니티: ").append(d.ranking().size()).append("팀")
				.append(" · 인정된 해시태그 글 ").append(String.format("%,d", d.totalPostCount())).append("건")
				.append(" · 참여 인원 ").append(String.format("%,d", d.totalParticipantCount())).append("명\n\n");
		
		if (!d.ranking().isEmpty()) {
			HashtagRankingRow winner = d.ranking().get(0);
			md.append("## 🏆 1위 ").append(winner.artistName())
					.append(" (참여율 ").append(winner.participationRate()).append("%)\n\n");
		}
		
		// 마크다운 표 - Toast UI Viewer 가 표로 그려준다
		md.append("| 순위 | 아티스트 | 해시태그 | 참여율 | 참여 인원 | 글 수 |\n");
		md.append("| --- | --- | --- | --- | --- | --- |\n");
		for (HashtagRankingRow row : d.ranking()) {
			md.append("| ").append(row.rank())
					.append(" | ").append(row.artistName())
					.append(" | ").append(row.hashtag())
					.append(" | ").append(row.participationRate()).append("%")
					.append(" | ").append(String.format("%,d / %,d명", row.participantCount(), row.memberCount()))
					.append(" | ").append(String.format("%,d", row.postCount()))
					.append(" |\n");
		}
		
		// 공지 본문 링크 → 공개 이벤트 페이지(그 회차)의 전체 순위로 바로 이동
		md.append("\n👉 [최종 순위 자세히 보기](/events/hashtag/").append(d.eventId()).append("?all=true#ranking)\n\n");
		md.append("다음 해시태그 총공에도 많은 참여 부탁드려요!");
		
		return new HashtagResultNoticeDraft("[이벤트] " + d.title() + " 결과 안내", md.toString());
	}
}