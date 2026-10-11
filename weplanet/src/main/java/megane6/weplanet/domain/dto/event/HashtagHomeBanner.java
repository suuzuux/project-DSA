package megane6.weplanet.domain.dto.event;

import megane6.weplanet.i18n.Messages;

import java.util.Locale;

/** 홈 총공 슬라이드 - 제목 번역 대기 중에는 한국어 문장 틀을 사용한다. */
public record HashtagHomeBanner(
		String kicker,		// 작은 윗줄 (예: 진행 중 · 종료까지 1일 4시간)
		String title,		// 큰 제목
		String body,		// 아랫줄 안내
		String linkUrl		// 클릭하면 갈 공개 이벤트 페이지
) {
	public static HashtagHomeBanner from(HashtagEventPageView view, String title, Locale titleLocale, Messages messages) {
		HashtagEventDashboard d = view.dashboard();
		String link = "/events/hashtag/" + d.eventId();
		
		return switch (d.status()) {
			case SCHEDULED -> new HashtagHomeBanner(
					messages.get("hashtagBanner.scheduled.kicker", view.remainingText()),
					messages.get(titleLocale, "hashtagBanner.scheduled.title", title),
					messages.get("hashtagBanner.scheduled.body"),
					link);
			case ONGOING -> new HashtagHomeBanner(
					messages.get("hashtagBanner.ongoing.kicker", view.remainingText()),
					messages.get(titleLocale, "hashtagBanner.ongoing.title", title),
					messages.get("hashtagBanner.ongoing.body"),
					link);
			case ENDED -> new HashtagHomeBanner(
					messages.get("hashtagBanner.ended.kicker"),
					messages.get(titleLocale, "hashtagBanner.ended.title", title),
					messages.get("hashtagBanner.ended.body"),
					link);
			case FINALIZED -> new HashtagHomeBanner(
					messages.get("hashtagBanner.finalized.kicker"),
					d.ranking().isEmpty()
							? messages.get(titleLocale, "hashtagBanner.finalized.titleNoRanking", title)
							: messages.get(titleLocale, "hashtagBanner.finalized.title", title, d.ranking().get(0).artistName()),
					messages.get("hashtagBanner.finalized.body"),
					link);
		};
	}
}
