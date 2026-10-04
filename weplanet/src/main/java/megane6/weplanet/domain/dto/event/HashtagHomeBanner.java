package megane6.weplanet.domain.dto.event;

import megane6.weplanet.i18n.Messages;

/**
 * 홈 캐러셀 맨 앞에 자동으로 붙는 해시태그 총공 슬라이드
 * 관리자가 배너를 따로 등록하지 않아도, 총공 상태에 맞는 문구로 알아서 바뀐다
 * 고정 문구는 메시지 키(hashtagBanner.*)로 현재 언어에 맞춰 만든다. 이벤트 제목/아티스트명은 입력값 그대로
 */
public record HashtagHomeBanner(
		String kicker,		// 작은 윗줄 (예 : 지금 진행 중 · 종료까지 1일 4시간 12분)
		String title,		// 큰 제목
		String body,		// 아랫줄 안내
		String linkUrl		// 클릭하면 갈 공개 이벤트 페이지
) {
	public static HashtagHomeBanner from(HashtagEventPageView view, Messages messages) {
		HashtagEventDashboard d = view.dashboard();
		String link = "/events/hashtag/" + d.eventId();
		
		return switch (d.status()) {
			case SCHEDULED -> new HashtagHomeBanner(
					messages.get("hashtagBanner.scheduled.kicker", view.remainingText()),
					messages.get("hashtagBanner.scheduled.title", d.title()),
					messages.get("hashtagBanner.scheduled.body"),
					link);
			case ONGOING -> new HashtagHomeBanner(
					messages.get("hashtagBanner.ongoing.kicker", view.remainingText()),
					messages.get("hashtagBanner.ongoing.title", d.title()),
					messages.get("hashtagBanner.ongoing.body"),
					link);
			case ENDED -> new HashtagHomeBanner(
					messages.get("hashtagBanner.ended.kicker"),
					messages.get("hashtagBanner.ended.title", d.title()),
					messages.get("hashtagBanner.ended.body"),
					link);
			case FINALIZED -> new HashtagHomeBanner(
					messages.get("hashtagBanner.finalized.kicker"),
					d.ranking().isEmpty()
							? messages.get("hashtagBanner.finalized.titleNoRanking", d.title())
							: messages.get("hashtagBanner.finalized.title", d.title(), d.ranking().get(0).artistName()),
					messages.get("hashtagBanner.finalized.body"),
					link);
		};
	}
}
