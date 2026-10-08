package megane6.weplanet.service;

import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.service.ContentTranslationService.Translation;
import megane6.weplanet.service.MainBannerService.Slide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MainBannerTranslatorTest {

	private final ContentTranslationService translationService = mock(ContentTranslationService.class);
	private final MainBannerTranslator translator = new MainBannerTranslator(translationService);
	private final Slide slide = new Slide("NOVA", "NOVA 정규 2집 컴백", "스텔라와 함께하는 새로운 여정",
			"/uploads/demo_banner_1_nova.jpg", "#4F46E5", "#FFFFFF", "/nova");

	@AfterEach
	void resetLocale() {
		LocaleContextHolder.resetLocaleContext();
	}

	// 한국어 화면은 AI 를 부르지 않고 그대로 보여준다
	@Test
	void koreanScreenKeepsOriginal() {
		LocaleContextHolder.setLocale(Locale.KOREAN);

		MainBannerTranslator.Result result = translator.localize(List.of(slide));

		assertSame(slide, result.slides().get(0));
		assertFalse(result.pending());
		verifyNoInteractions(translationService);
	}

	// 일본어 화면은 제목·본문만 번역문으로 바꾸고 나머지(이미지·링크·색)는 그대로
	@Test
	void otherLanguageUsesTranslation() {
		LocaleContextHolder.setLocale(Locale.JAPANESE);
		when(translationService.translateAll(anyList(), eq(Language.JA), any()))
				.thenReturn(List.of(Optional.of(new Translation("NOVA 正規2集カムバック", "ステラと一緒の新しい旅"))));

		MainBannerTranslator.Result result = translator.localize(List.of(slide));
		Slide translated = result.slides().get(0);

		assertEquals("NOVA 正規2集カムバック", translated.title());
		assertEquals("ステラと一緒の新しい旅", translated.body());
		assertEquals(slide.imageUrl(), translated.imageUrl());
		assertEquals(slide.linkUrl(), translated.linkUrl());
		assertFalse(result.pending());
	}

	// 번역이 없으면 원문과 pending (화면이 다시 받아 교체)
	@Test
	void missingTranslationKeepsOriginalAndIsPending() {
		LocaleContextHolder.setLocale(Locale.ENGLISH);
		when(translationService.translateAll(anyList(), eq(Language.EN), any())).thenReturn(List.of(Optional.empty()));

		MainBannerTranslator.Result result = translator.localize(List.of(slide));

		assertSame(slide, result.slides().get(0));
		assertTrue(result.pending());
	}
}
