package megane6.weplanet.service.main;
import megane6.weplanet.service.chat.AiFanChatService;

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

/** Gemini API 호출 클라이언트 (요약·번역·AI 팬 채팅 공통). */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    // AI 실패 안내 문구 번역
    private final Messages messages;

    // API 키는 설정 파일에서 읽는다.
    @Value("${gemini.api.key}")
    private String apiKey;

    // 배너·공지 번역 전용 키 (없으면 공용 키)
    @Value("${gemini.translation.api.key:}")
    private String translationApiKey;

    // HTTP 요청 도구
    private final RestTemplate restTemplate = new RestTemplate();
    private final ThreadLocal<String> lastFailure = new ThreadLocal<>();

    // 요약·번역·DM 모델 (라이브 댓글은 Flash-Lite)
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";
    private static final String GEMINI_LIVE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";

    /** 프롬프트를 보내고 답변 텍스트를 돌려준다 (실패하면 안내 문구). */
    public String generate(String prompt) {
        return generate(prompt, false);
    }

    // AI 팬 답장 JSON 요청 (실패하면 null).
    public String generateJson(String prompt) {
        return generate(prompt, true);
    }

    // 라이브 댓글·AI 팬 DM (Flash-Lite, 거절되면 번역 키로 재시도).
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

    // 배너·공지 번역 전용 (번역 키로 한도를 분리, 실패하면 null).
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
            // Gemini 요청 형식
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
            headers.set("x-goog-api-key", key); // API 키

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Gemini 호출
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            String text = extractText(response);
            if (text == null || text.isBlank()) {
                lastFailure.set("응답 본문이 비었습니다");
            }
            return text;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // 오류 본문까지 기록한다.
            String detail = e.getStatusCode().value() == 429
                    ? "사용 한도를 초과했습니다. 잠시 후 다시 시도하거나 다른 API 키를 넣어 주세요."
                    : e.getStatusCode().value() + " " + abbreviate(e.getResponseBodyAsString());
            lastFailure.set(detail);
            log.warn("Gemini API 호출 실패: {}", detail);
            return jsonResponse ? null : messages.get("error.ai.unavailable");
        } catch (RestClientException e) {
            // 한도 초과·네트워크 오류는 안내 문구로 대체한다.
            lastFailure.set(abbreviate(e.getMessage()));
            log.warn("Gemini API 호출 실패: {}", e.getMessage());
            return jsonResponse ? null : messages.get("error.ai.unavailable");
        } catch (RuntimeException e) {
            // 응답 구조가 예상과 다른 경우 (안전성 필터 등)
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

    // Gemini 응답의 중첩 구조에서 텍스트만 꺼낸다.
    @SuppressWarnings("unchecked") // 응답 구조가 고정이라 형변환 경고를 무시한다.
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
        // 답 텍스트가 없을 때만 추론 텍스트를 쓴다.
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
