package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.i18n.Messages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 구글의 AI 모델인 Gemini에게 "이런 질문/글을 줄 테니 답을 만들어줘" 하고 요청을 보내는 담당 클래스.
 * FEED-09(AI 요약), CHAT-06(AI 팬 채팅)이 공통으로 이 클래스를 사용함.
 * <p>
 * 지금까지 우리 서버는 "브라우저 → 우리 서버 → DB" 구조로만 데이터를 주고받았는데,
 * 여기서는 "우리 서버 → 구글의 Gemini 서버"로 인터넷 너머 다른 회사 서버에 직접 요청을 보냄.
 * RestTemplate이 그 역할(HTTP 요청을 보내고 응답을 받는 것)을 해주는 도구.
 * <p>
 *
 * @Component : @Service와 거의 같은 역할. "이 클래스는 스프링이 관리하는 부품(빈)이다"라는 표시.
 * 특정 계층(서비스/컨트롤러 등)에 딱 맞지 않는 공용 도구성 클래스일 때 흔히 씀.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    // AI 요약/번역 실패 안내 문구를 요청 로케일로 돌려준다
    private final Messages messages;

    // application.properties에 적어둔 gemini.api.key 값을 이 필드에 자동으로 넣어줌
    // (API 키 자체를 코드에 직접 쓰지 않고 설정 파일에서 읽어오는 이유 : 키가 외부에 노출되는 걸 막기 위함)
    @Value("${gemini.api.key}")
    private String apiKey;

    // 메인 배너·공지 번역 전용 키 (선택) - 비어 있으면 위 공용 키를 쓴다
    @Value("${gemini.translation.api.key:}")
    private String translationApiKey;

    // 외부 서버에 HTTP 요청을 보낼 때 쓰는 스프링 제공 도구
    private final RestTemplate restTemplate = new RestTemplate();
    private final ThreadLocal<String> lastFailure = new ThreadLocal<>();

    // 요약·번역·DM. 라이브 댓글은 더 싼 Flash-Lite를 쓴다 (무료 한도도 모델마다 따로다).
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";
    private static final String GEMINI_LIVE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";

    /**
     * 프롬프트(질문/지시문) 하나를 Gemini에게 보내고, 답변 텍스트만 뽑아서 돌려줌.
     * <p>
     * try-catch로 감싸둔 이유 : 이건 우리 서버가 아니라 "인터넷 건너편 남의 서버"를 호출하는 거라서,
     * 언제든 실패할 수 있음 (요청이 너무 많아서 거절당함, 인터넷이 잠깐 끊김 등).
     * 이럴 때 예외를 그냥 던져버리면 화면 전체가 에러 페이지로 깨져버리므로,
     * 실패하면 대신 "지금은 이용할 수 없다"는 안내 문구를 돌려줘서 서비스가 멈추지 않게 함.
     */
    public String generate(String prompt) {
        return generate(prompt, false);
    }

    // AI 팬 5명의 답장을 한 번에 JSON으로 받을 때 사용.
    // 실패하면 null 을 돌려줘서 호출부(AiFanChatService)가 언어와 상관없이 실패를 알아채고 페르소나 fallback 문구를 쓰게 한다.
    public String generateJson(String prompt) {
        return generate(prompt, true);
    }

    // 라이브 댓글과 AI 팬 DM. 한도가 남은 Flash-Lite를 쓰고, 공용 키가 거절되면 번역 전용 키로 한 번 더 시도한다.
    public String generateLiveJson(String prompt) {
        String text = generate(prompt, true, apiKey, false, GEMINI_LIVE_URL);
        if (text != null && !text.isBlank()) {
            return text;
        }
        boolean otherKey = translationApiKey != null && !translationApiKey.isBlank() && !translationApiKey.equals(apiKey);
        if (!otherKey) {
            return text;
        }
        log.warn("라이브 댓글 Gemini를 번역 전용 키로 재시도");
        return generate(prompt, true, translationApiKey, false, GEMINI_LIVE_URL);
    }

    public String lastFailure() {
        String value = lastFailure.get();
        return value == null ? "" : value;
    }

    // 메인 배너·공지 번역(ContentTranslationService) 전용. 번역 전용 키가 있으면 그 키로 보내서
    // 다른 AI 기능과 하루 한도를 나눠 쓰지 않게 한다. 실패하면 generateJson 과 같이 null
    public String generateTranslationJson(String prompt) {
        boolean hasTranslationKey = translationApiKey != null && !translationApiKey.isBlank();
        return generate(prompt, true, hasTranslationKey ? translationApiKey : apiKey);
    }

    private String generate(String prompt, boolean jsonResponse) {
        return generate(prompt, jsonResponse, apiKey);
    }

    private String generate(String prompt, boolean jsonResponse, String key) {
        return generate(prompt, jsonResponse, key, false);
    }

    private String generate(String prompt, boolean jsonResponse, String key, boolean minimalThinking) {
        return generate(prompt, jsonResponse, key, minimalThinking, GEMINI_URL);
    }

    private String generate(String prompt, boolean jsonResponse, String key, boolean minimalThinking, String url) {
        lastFailure.remove();
        try {
            // Gemini가 요구하는 JSON 형식에 맞춰서 요청 내용을 만듦
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(
                    Map.of("parts", List.of(
                            Map.of("text", prompt)
                    ))
            ));
            if (jsonResponse) {
                Map<String, Object> config = new HashMap<>();
                config.put("responseMimeType", "application/json");
                if (minimalThinking) {
                    config.put("thinkingConfig", Map.of("thinkingLevel", "MINIMAL", "includeThoughts", false));
                }
                requestBody.put("generationConfig", config);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", key); // 이 요청이 우리 서비스에서 보낸 게 맞다는 걸 증명하는 열쇠

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // 실제로 Gemini 서버에 요청을 보내고, 응답(JSON)을 받아옴
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            String text = extractText(response);
            if (text == null || text.isBlank()) {
                lastFailure.set("응답 본문이 비었습니다");
            }
            return text;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // 4xx/5xx 본문(모델명 오류, 쿼터 초과 이유)까지 남긴다
            String detail = e.getStatusCode().value() == 429
                    ? "사용 한도를 초과했습니다. 잠시 후 다시 시도하거나 다른 API 키를 넣어 주세요."
                    : e.getStatusCode().value() + " " + abbreviate(e.getResponseBodyAsString());
            lastFailure.set(detail);
            log.warn("Gemini API 호출 실패: {}", detail);
            return jsonResponse ? null : messages.get("error.ai.unavailable");
        } catch (RestClientException e) {
            // Gemini API 하루 사용 한도 초과(HTTP 429), 네트워크 오류 등 - 서비스 전체가 죽지 않고 안내 문구로 대체
            lastFailure.set(abbreviate(e.getMessage()));
            log.warn("Gemini API 호출 실패: {}", e.getMessage());
            return jsonResponse ? null : messages.get("error.ai.unavailable");
        } catch (RuntimeException e) {
            // 안전성 필터로 candidates가 비어 오는 등 응답 구조가 예상과 다른 경우.
            // RestClientException으로는 안 잡혀서 그대로 두면 NPE가 500 에러로 터졌음
            lastFailure.set(abbreviate(e.toString()));
            log.warn("Gemini 응답 해석 실패: {}", e.toString());
            return jsonResponse ? null : messages.get("error.ai.unavailable");
        }
    }

    private static String abbreviate(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.strip().replaceAll("\\s+", " ");
        return text.length() <= 180 ? text : text.substring(0, 180);
    }

    // Gemini의 응답은 { candidates: [ { content: { parts: [ { text: "..." } ] } } ] } 같은
    // 복잡한 중첩 구조로 옴. 그 안에서 우리가 진짜 필요한 텍스트 한 줄만 꺼내는 메서드
    @SuppressWarnings("unchecked") // Map<String,Object>를 강제로 형변환할 때 뜨는 경고를 무시함 (Gemini 응답 구조가 고정돼 있어서 안전함)
    private String extractText(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            log.warn("Gemini 응답에 candidates 없음: {}", response.get("promptFeedback"));
            return null;
        }
        Map<String, Object> contentMap = (Map<String, Object>) candidates.get(0).get("content");
        if (contentMap == null) {
            log.warn("Gemini candidate에 content 없음. finishReason={}", candidates.get(0).get("finishReason"));
            return null;
        }
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        // 추론 part가 앞에 오면 그걸 답으로 쓰면 JSON 파싱이 실패한다. 답 텍스트가 없을 때만 추론 텍스트를 쓴다.
        String answer = null;
        String thought = null;
        for (Map<String, Object> part : parts) {
            Object text = part.get("text");
            if (!(text instanceof String value) || value.isBlank()) {
                continue;
            }
            if (Boolean.TRUE.equals(part.get("thought"))) {
                thought = value;
            } else {
                answer = value;
            }
        }
        return answer != null ? answer : thought;
    }
}
