package megane6.weplanet.domain.dto.event;

/**
 * 홈 캐러셀 맨 앞에 자동으로 붙는 해시태그 총공 슬라이드
 * 관리자가 배너를 따로 등록하지 않아도, 총공 상태에 맞는 문구로 알아서 바뀐다
 */
public record HashtagHomeBanner(
		String kicker,		// 작은 윗줄 (예 : 지금 지금 진행 중 · 종료까지 1일 4시간 12분)
		String title,		// 큰 제목
		String body,		// 아랫줄 안내
		String linkUrl		// 클릭하면 갈 공개 이벤트 페이지
) {
	public static HashtagHomeBanner from (HashtagEventPageView view) {
		HashtagEventDashboard d = view.dashboard();
		String link = "/events/hashtag/" + d.eventId();
		
		return switch (d.status()) {
			case SCHEDULED -> new HashtagHomeBanner(
					"Coming soon · 시작까지 " + view.remainingText(),
					d.title() + " 곧 시작해요!",
					"참여 아티스트와 해시태그 미리 보기 →",
					link);
			case ONGOING -> new HashtagHomeBanner(
					"지금 진행 중 · 종료까지 " + view.remainingText(),
					d.title() + ", 우리 아티스트는 몇 위?",
					"실시간 순위 보기 →",
					link);
			case ENDED -> new HashtagHomeBanner(
					"집계 중",
					d.title() + " 종료! 참여해주셔서 감사해요",
					"최종 결과는 곧 공지로 안내돼요 →",
					link);
			case FINALIZED -> new HashtagHomeBanner(
					"최종 결과",
					d.ranking().isEmpty()
							? d.title() + " 결과 발표"
							: d.title() + " 1위는 " + d.ranking().get(0).artistName() + "!",
					"전체 결과 보기 →",
					link);
		};
	}
}
