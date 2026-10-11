package megane6.weplanet.controller.common;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.event.HashtagEventPageView;
import megane6.weplanet.service.main.MainBannerService;
import megane6.weplanet.service.main.MainBannerService.Slide;
import megane6.weplanet.service.main.MainBannerTranslator;
import megane6.weplanet.service.event.HashtagEventPageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/** 메인 배너 번역문 API - 늦게 끝난 번역을 화면이 다시 받아 교체한다. */
@RestController
@RequiredArgsConstructor
public class MainBannerApiController {

	private final MainBannerService mainBannerService;
	private final MainBannerTranslator mainBannerTranslator;
	private final HashtagEventPageService hashtagEventPageService;

	// originalTitle 로 화면의 어느 배너인지 맞춘다.
	@GetMapping("/api/main-banners")
	public Map<String, Object> translated() {
		List<Slide> originals = mainBannerService.activeSlides();
		Optional<HashtagEventPageView> eventView = hashtagEventPageService.getHomeBanner();
		String eventTitle = eventView.map(view -> view.dashboard().title()).orElse(null);
		MainBannerTranslator.Result result = mainBannerTranslator.localize(originals, eventTitle, MainBannerTranslator.FOLLOW_UP_WAIT);
		List<Map<String, String>> banners = new ArrayList<>();
		for (int i = 0; i < originals.size(); i++) {
			Map<String, String> banner = new HashMap<>();
			banner.put("originalTitle", originals.get(i).title());
			banner.put("title", result.slides().get(i).title());
			banner.put("body", result.slides().get(i).body());
			banners.add(banner);
		}
		Map<String, Object> response = new HashMap<>();
		response.put("pending", result.pending());
		response.put("banners", banners);
		// 총공 슬라이드 제목 문장 (번역된 이벤트 제목 + 언어 파일 문장 틀). 총공이 없으면 넣지 않는다
		eventView.ifPresent(view -> response.put("hashtagTitle", hashtagEventPageService.toHomeBanner(view, result.eventTitle(), result.eventTitleReady()).title()));
		return response;
	}
}
