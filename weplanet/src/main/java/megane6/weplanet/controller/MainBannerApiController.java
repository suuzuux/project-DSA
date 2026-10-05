package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.service.MainBannerService;
import megane6.weplanet.service.MainBannerService.Slide;
import megane6.weplanet.service.MainBannerTranslator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 메인 배너 번역문 (main-banner-translate.js). 메인 화면을 그릴 때 번역이 늦어 원문으로 둔 배너가 있으면
 * 화면이 이 주소로 번역문을 다시 받아 글자만 바꿔 끼운다 - 언어를 바꾼 직후에도 새로고침 없이 번역이 보이게.
 * 진행 중인 번역이 있으면 그것을 같이 기다리므로 AI 를 새로 부르지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class MainBannerApiController {

	private final MainBannerService mainBannerService;
	private final MainBannerTranslator mainBannerTranslator;

	// originalTitle 은 화면의 어느 배너인지 맞춰 보는 용도 (그 사이 노출 배너가 바뀌었으면 화면이 건너뛴다)
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
