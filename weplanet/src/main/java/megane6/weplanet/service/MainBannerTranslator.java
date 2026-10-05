package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.service.ContentTranslationService.Source;
import megane6.weplanet.service.ContentTranslationService.Translation;
import megane6.weplanet.service.MainBannerService.Slide;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 메인 화면 배너 제목·본문을 지금 화면 언어로 (관리자가 입력한 글이라 언어 파일이 아니라 AI 번역).
 * 한국어 화면은 그대로 보여주고, 처음 보는 배너는 최대 PAGE_WAIT 까지만 기다린 뒤 원문으로 보여준다.
 * 그때는 pending=true 라서 메인 화면이 /api/main-banners 로 번역문을 다시 받아 글자만 바꿔 끼운다 (새로고침 없이).
 */
@Component
@RequiredArgsConstructor
public class MainBannerTranslator {

	// 메인 화면을 그릴 때 기다리는 시간 / 화면이 뒤에서 번역문을 다시 받을 때 기다리는 시간
	public static final Duration PAGE_WAIT = Duration.ofSeconds(3);
	public static final Duration FOLLOW_UP_WAIT = Duration.ofSeconds(20);

	private final ContentTranslationService translationService;

	// slides = 화면에 보여줄 배너, pending = 아직 번역이 안 끝나 원문으로 둔 배너가 있음
	public record Result(List<Slide> slides, boolean pending) {}

	public Result localize(List<Slide> slides) {
		return localize(slides, PAGE_WAIT);
	}

	public Result localize(List<Slide> slides, Duration maxWait) {
		Language language = PreferredLocaleResolver.toLanguage(LocaleContextHolder.getLocale());
		if (language == Language.KO || slides.isEmpty()) {
			return new Result(slides, false);
		}
		List<Source> sources = slides.stream().map(slide -> new Source(slide.title(), slide.body())).toList();
		List<Optional<Translation>> translations = translationService.translateAll(sources, language, maxWait);
		List<Slide> localized = new ArrayList<>(slides.size());
		boolean pending = false;
		for (int i = 0; i < slides.size(); i++) {
			Slide slide = slides.get(i);
			Optional<Translation> translation = translations.get(i);
			pending |= translation.isEmpty();
			localized.add(translation
					.map(t -> new Slide(slide.label(), t.title(), t.body(), slide.imageUrl(),
							slide.bgColor(), slide.textColor(), slide.linkUrl()))
					.orElse(slide));
		}
		return new Result(localized, pending);
	}
}
