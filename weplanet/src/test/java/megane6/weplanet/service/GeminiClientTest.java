package megane6.weplanet.service;

import megane6.weplanet.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 번역 전용 키 선택만 확인한다 (실제 Gemini 서버로는 요청을 보내지 않음)
class GeminiClientTest {

	private static final String ANSWER =
			"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"title\\\":\\\"T\\\"}\"}]}}]}";

	private final GeminiClient client = new GeminiClient(mock(Messages.class));
	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(client, "apiKey", "team-key");
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
		server = MockRestServiceServer.bindTo(restTemplate).build();
	}

	// 번역 전용 키가 있으면 배너·공지 번역은 그 키로, 다른 AI 기능은 그대로 공용 키로 보낸다
	@Test
	void translationUsesItsOwnKeyWhenConfigured() {
		ReflectionTestUtils.setField(client, "translationApiKey", "translation-key");
		expectKey("translation-key");
		expectKey("team-key");

		assertEquals("{\"title\":\"T\"}", client.generateTranslationJson("번역"));
		assertEquals("{\"title\":\"T\"}", client.generateJson("요약"));
		server.verify();
	}

	// 번역 전용 키가 없으면(키를 안 넣은 PC) 번역도 공용 키를 쓴다
	@Test
	void translationFallsBackToTeamKey() {
		ReflectionTestUtils.setField(client, "translationApiKey", "");
		expectKey("team-key");

		assertEquals("{\"title\":\"T\"}", client.generateTranslationJson("번역"));
		server.verify();
	}

	private void expectKey(String key) {
		server.expect(requestTo(startsWith("https://generativelanguage.googleapis.com/")))
				.andExpect(header("x-goog-api-key", key))
				.andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
	}
}
