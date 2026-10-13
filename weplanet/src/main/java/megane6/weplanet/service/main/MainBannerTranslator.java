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

	// pending은 미완료 번역 여부, eventTitleReady는 총공 제목의 화면 언어 준비 여부다.
	public record Result(List<Slide> slides, String eventTitle, boolean eventTitleReady, boolean pending) {}

	public Result localize(List<Slide> slides) {
		return localize(slides, null, PAGE_WAIT);
	}

	public Result localize(List<Slide> slides, String eventTitle) {
		return localize(slides, eventTitle, PAGE_WAIT);
	}

	public Result localize(List<Slide> slides, String eventTitle, Duration maxWait) {
		Language language = PreferredLocaleResolver.toLanguage(LocaleContextHolder.getLocale());
		boolean hasEventTitle = eventTitle != null && !eventTitle.isBlank();
		if (language == Language.KO || slides.isEmpty() && !hasEventTitle) {
			return new Result(slides, eventTitle, true, false);
		}
		List<Source> sources = new ArrayList<>();
		slides.forEach(slide -> sources.add(new Source(slide.title(), slide.body())));
		if (hasEventTitle) {
			sources.add(new Source(eventTitle, null));
		}
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
		String localizedEventTitle = eventTitle;
		boolean eventTitleReady = true;
		if (hasEventTitle) {
			Optional<Translation> translation = translations.get(slides.size()); // 맨 뒤 = 총공 제목
			pending |= translation.isEmpty();
			eventTitleReady = translation.isPresent();
			localizedEventTitle = translation.map(Translation::title).orElse(eventTitle);
		}
		return new Result(localized, localizedEventTitle, eventTitleReady, pending);
	}

	// 총공 이벤트 페이지 제목 - 홈 배너와 같은 번역 기억을 쓴다 (배너에서 이미 번역했으면 AI 를 다시 부르지 않음)
	public String localizeEventTitle(String eventTitle) {
		return localize(List.of(), eventTitle).eventTitle();
	}
}
