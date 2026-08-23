package megane6.weplanet.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

// FEED-09(AI 요약), CHAT-06(AI 팬 채팅)이 공통으로 사용하는 Gemini API 호출 클라이언트
@Slf4j
@Component
public class GeminiClient {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";

    // 프롬프트 하나를 보내고, 응답 텍스트만 뽑아서 돌려줌
    // API 할당량 초과, 네트워크 오류 등으로 실패해도 화면이 깨지지 않도록 여기서 한 번에 방어
    public String generate(String prompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            Map<String, Object> response = restTemplate.postForObject(GEMINI_URL, request, Map.class);

            return extractText(response);
        } catch (RestClientException e) {
            // Gemini API 할당량 초과(429), 네트워크 오류 등 - 서비스 전체가 죽지 않고 안내 문구로 대체
            log.warn("Gemini API 호출 실패: {}", e.getMessage());
            return "지금은 AI 응답을 받아올 수 없어요. 잠시 후 다시 시도해주세요.";
        }
    }

    // Gemini 응답 구조(candidates -> content -> parts -> text)에서 실제 텍스트만 꺼냄
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> firstCandidate = candidates.get(0);
        Map<String, Object> contentMap = (Map<String, Object>) firstCandidate.get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
        return (String) parts.get(0).get("text");
    }
}
