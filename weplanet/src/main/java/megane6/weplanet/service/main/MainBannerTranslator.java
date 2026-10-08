package megane6.weplanet.service.main;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.service.main.ContentTranslationService.Source;
import megane6.weplanet.service.main.ContentTranslationService.Translation;
import megane6.weplanet.service.main.MainBannerService.Slide;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 배너 제목·본문 AI 번역 (최대 PAGE_WAIT 대기 후 원문, pending 이면 화면이 다시 받아 교체). */
@Component
@RequiredArgsConstructor
public class MainBannerTranslator {

	// 메인 화면 렌더링 대기 시간
	public static final Duration PAGE_WAIT = Duration.ofSeconds(3);
	public static final Duration FOLLOW_UP_WAIT = Duration.ofSeconds(20);

	private final ContentTranslationService translationService;

	// slides 는 화면 배너, pending 은 아직 번역 중이라 원문으로 둔 배너가 있는지
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
