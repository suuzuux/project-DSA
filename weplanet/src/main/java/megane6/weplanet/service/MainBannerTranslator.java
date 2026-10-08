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

	// slides = 화면에 보여줄 배너, pending = 아직 번역이 안 끝나 원문으로 둔 배너가 있음, eventTitle = 총공 슬라이드 제목(없으면 null)
	public record Result(List<Slide> slides, String eventTitle, boolean pending) {}

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
			return new Result(slides, eventTitle, false);
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
		if (hasEventTitle) {
			Optional<Translation> translation = translations.get(slides.size()); // 맨 뒤 = 총공 제목
			pending |= translation.isEmpty();
			localizedEventTitle = translation.map(Translation::title).orElse(eventTitle);
		}
		return new Result(localized, localizedEventTitle,pending);
	}

	// 총공 이벤트 페이지 제목 - 홈 배너와 같은 번역 기억을 쓴다 (배너에서 이미 번역했으면 AI 를 다시 부르지 않음)
	public String localizeEventTitle(String eventTitle) {
		return localize(List.of(), eventTitle).eventTitle();
	}
}
