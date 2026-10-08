package megane6.weplanet.domain.dto.event;

import megane6.weplanet.i18n.Messages;

import java.util.Locale;

/**
 * 홈 캐러셀 맨 앞에 자동으로 붙는 해시태그 총공 슬라이드
 * 관리자가 배너를 따로 등록하지 않아도, 총공 상태에 맞는 문구로 알아서 바뀐다
 * 고정 문구는 메시지 키(hashtagBanner.*)로 현재 언어에 맞춰 만든다. 이벤트 제목/아티스트명은 입력값 그대로
 * 큰 제목만은 titleLocale 언어의 문장 틀로 만든다 - 제목 번역을 기다리는 동안 한국어 제목 + 일본어 틀이 한 줄에 섞이지 않게
 */
public record HashtagHomeBanner(
		String kicker,		// 작은 윗줄 (예 : 지금 진행 중 · 종료까지 1일 4시간 12분)
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
