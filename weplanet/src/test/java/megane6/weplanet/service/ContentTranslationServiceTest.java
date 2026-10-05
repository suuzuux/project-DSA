package megane6.weplanet.service;

import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.service.ContentTranslationService.Source;
import megane6.weplanet.service.ContentTranslationService.Translation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentTranslationServiceTest {

	private final GeminiClient geminiClient = mock(GeminiClient.class);
	private final Messages messages = mock(Messages.class);

	private ContentTranslationService service(Executor executor) {
		return new ContentTranslationService(geminiClient, JsonMapper.builder().build(), messages, executor);
	}

	// 제목·본문을 한 번에 번역하고, 같은 글·같은 언어는 다시 AI 를 부르지 않는다
	@Test
	void translatesTitleAndBodyAndRemembersResult() {
		when(geminiClient.generateTranslationJson(anyString()))
				.thenReturn("[{\"title\":\"NOVA 2nd Album Comeback\",\"body\":\"A new journey with Stella\"}]");
		ContentTranslationService service = service(Runnable::run);

		Optional<Translation> first = service.translate("NOVA 정규 2집 컴백", "스텔라와 함께하는 새로운 여정", Language.EN);
		Optional<Translation> second = service.translate("NOVA 정규 2집 컴백", "스텔라와 함께하는 새로운 여정", Language.EN);

		assertEquals("NOVA 2nd Album Comeback", first.orElseThrow().title());
		assertEquals("A new journey with Stella", first.orElseThrow().body());
		assertEquals(first, second);
		verify(geminiClient, times(1)).generateTranslationJson(anyString());
	}

	// AI 가 실패하면 empty 를 돌려주고 기억하지 않는다 (다음에 다시 시도)
	@Test
	void failureReturnsEmptyAndIsNotRemembered() {
		when(geminiClient.generateTranslationJson(anyString())).thenReturn(null);
		ContentTranslationService service = service(Runnable::run);

		assertTrue(service.translate("제목", "본문", Language.JA).isEmpty());
		assertTrue(service.translate("제목", "본문", Language.JA).isEmpty());
		verify(geminiClient, times(2)).generateTranslationJson(anyString());
	}

	// 본문이 없는 글(본문 없는 배너)은 번역해도 본문이 null 그대로다
	@Test
	void missingBodyStaysNull() {
		when(geminiClient.generateTranslationJson(anyString())).thenReturn("[{\"title\":\"Concert\",\"body\":\"\"}]");

		Translation translation = service(Runnable::run).translate("콘서트", null, Language.EN).orElseThrow();

		assertEquals("Concert", translation.title());
		assertNull(translation.body());
	}

	// 배너 여러 장은 AI 호출 한 번에 번역하고 (무료 한도 분당 5회), 같은 배너가 두 번 있어도 한 번만 보낸다
	@Test
	void translateAllSendsAllBannersInOneCall() {
		when(geminiClient.generateTranslationJson(anyString()))
				.thenReturn("[{\"title\":\"T1\",\"body\":\"B1\"},{\"title\":\"T2\",\"body\":\"\"}]");
		ContentTranslationService service = service(Runnable::run);
		List<Source> banners = List.of(new Source("제목1", "본문1"), new Source("제목2", null), new Source("제목1", "본문1"));

		List<Optional<Translation>> result = service.translateAll(banners, Language.EN, Duration.ofSeconds(1));

		assertEquals(new Translation("T1", "B1"), result.get(0).orElseThrow());
		assertEquals(new Translation("T2", null), result.get(1).orElseThrow());
		assertEquals(new Translation("T1", "B1"), result.get(2).orElseThrow());
		verify(geminiClient, times(1)).generateTranslationJson(anyString());

		// 다음 방문은 기억해 둔 번역을 쓴다
		service.translateAll(banners, Language.EN, Duration.ofSeconds(1));
		verify(geminiClient, times(1)).generateTranslationJson(anyString());
	}

	// AI 가 개수를 다르게 돌려주면 어느 번역이 어느 배너 것인지 알 수 없으므로 모두 원문
	@Test
	void translateAllRejectsAnswerWithWrongCount() {
		when(geminiClient.generateTranslationJson(anyString())).thenReturn("[{\"title\":\"T1\",\"body\":\"B1\"}]");

		List<Optional<Translation>> result = service(Runnable::run).translateAll(
				List.of(new Source("제목1", "본문1"), new Source("제목2", "본문2")), Language.EN, Duration.ofSeconds(1));

		assertTrue(result.get(0).isEmpty());
		assertTrue(result.get(1).isEmpty());
	}

	// 기다리는 시간 안에 끝나지 않으면 empty (화면은 원문을 보여준다)
	@Test
	void translateAllGivesUpAfterMaxWait() {
		Executor neverRuns = task -> { };

		List<Optional<Translation>> result = service(neverRuns)
				.translateAll(List.of(new Source("제목", "본문")), Language.EN, Duration.ofMillis(50));

		assertTrue(result.get(0).isEmpty());
	}

	// 배너 번역이 통째로 실패하면 한 번만 더 시도하고, 그래도 실패하면 한동안 다시 부르지 않는다
	// (AI 한도 초과 때 메인 화면을 열 때마다 기다리지 않게). 사용자가 직접 누르는 공지 번역보기는 그와 상관없이 다시 시도한다
	@Test
	void failedBannerTranslationIsNotRetriedRightAway() {
		when(geminiClient.generateTranslationJson(anyString())).thenReturn(null);
		ContentTranslationService service = service(Runnable::run);
		List<Source> banner = List.of(new Source("제목", "본문"));

		assertTrue(service.translateAll(banner, Language.EN, Duration.ofSeconds(5)).get(0).isEmpty());
		verify(geminiClient, times(2)).generateTranslationJson(anyString());
		assertTrue(service.translateAll(banner, Language.EN, Duration.ofSeconds(5)).get(0).isEmpty());
		verify(geminiClient, times(2)).generateTranslationJson(anyString());

		service.translate("제목", "본문", Language.EN);
		verify(geminiClient, times(3)).generateTranslationJson(anyString());
	}

	// Gemini 가 잠깐 몰려 첫 시도가 실패해도(503) 다시 시도해서 번역되면 그 번역을 쓴다
	@Test
	void bannerTranslationSucceedsOnSecondTry() {
		when(geminiClient.generateTranslationJson(anyString()))
				.thenReturn(null)
				.thenReturn("[{\"title\":\"T\",\"body\":\"B\"}]");

		List<Optional<Translation>> result = service(Runnable::run)
				.translateAll(List.of(new Source("제목", "본문")), Language.EN, Duration.ofSeconds(5));

		assertEquals(new Translation("T", "B"), result.get(0).orElseThrow());
		verify(geminiClient, times(2)).generateTranslationJson(anyString());
	}

	// 공지 번역보기 응답: 실패하면 success=false 와 AI 사용 불가 안내 문구
	@Test
	void noticeResponseExplainsFailure() {
		when(geminiClient.generateTranslationJson(anyString())).thenReturn(null);
		when(messages.get("error.ai.unavailable")).thenReturn("AI is unavailable right now.");

		Map<String, Object> response = service(Runnable::run).noticeResponse("공지", "내용", Language.EN);

		assertEquals(false, response.get("success"));
		assertEquals("AI is unavailable right now.", response.get("message"));
	}
}
