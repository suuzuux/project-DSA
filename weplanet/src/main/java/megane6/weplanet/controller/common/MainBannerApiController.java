package megane6.weplanet.controller.common;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.service.main.MainBannerService;
import megane6.weplanet.service.main.MainBannerService.Slide;
import megane6.weplanet.service.main.MainBannerTranslator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 메인 배너 번역문 API - 늦게 끝난 번역을 화면이 다시 받아 교체한다. */
@RestController
@RequiredArgsConstructor
public class MainBannerApiController {

	private final MainBannerService mainBannerService;
	private final MainBannerTranslator mainBannerTranslator;

	// originalTitle 로 화면의 어느 배너인지 맞춘다.
	@GetMapping("/api/main-banners")
	public Map<String, Object> translated() {
		List<Slide> originals = mainBannerService.activeSlides();
		MainBannerTranslator.Result result = mainBannerTranslator.localize(originals, MainBannerTranslator.FOLLOW_UP_WAIT);
		List<Map<String, String>> banners = new ArrayList<>();
		for (int i = 0; i < originals.size(); i++) {
			Map<String, String> banner = new HashMap<>();
			banner.put("originalTitle", originals.get(i).title());
			banner.put("title", result.slides().get(i).title());
			banner.put("body", result.slides().get(i).body());
			banners.add(banner);
		}
		return Map.of("pending", result.pending(), "banners", banners);
	}
}
