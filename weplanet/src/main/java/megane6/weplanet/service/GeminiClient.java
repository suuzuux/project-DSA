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
public class GeminiClient {

    // application.properties에 적어둔 gemini.api.key 값을 이 필드에 자동으로 넣어줌
    // (API 키 자체를 코드에 직접 쓰지 않고 설정 파일에서 읽어오는 이유 : 키가 외부에 노출되는 걸 막기 위함)
    @Value("${gemini.api.key}")
    private String apiKey;

    // 외부 서버에 HTTP 요청을 보낼 때 쓰는 스프링 제공 도구
    private final RestTemplate restTemplate = new RestTemplate();

    // Gemini AI 서버의 주소(엔드포인트)
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";

    /**
     * 프롬프트(질문/지시문) 하나를 Gemini에게 보내고, 답변 텍스트만 뽑아서 돌려줌.
     * <p>
     * try-catch로 감싸둔 이유 : 이건 우리 서버가 아니라 "인터넷 건너편 남의 서버"를 호출하는 거라서,
     * 언제든 실패할 수 있음 (요청이 너무 많아서 거절당함, 인터넷이 잠깐 끊김 등).
     * 이럴 때 예외를 그냥 던져버리면 화면 전체가 에러 페이지로 깨져버리므로,
     * 실패하면 대신 "지금은 이용할 수 없다"는 안내 문구를 돌려줘서 서비스가 멈추지 않게 함.
     */
    public String generate(String prompt) {
        try {
            // Gemini가 요구하는 JSON 형식에 맞춰서 요청 내용을 만듦
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey); // 이 요청이 우리 서비스에서 보낸 게 맞다는 걸 증명하는 열쇠

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // 실제로 Gemini 서버에 요청을 보내고, 응답(JSON)을 받아옴
            Map<String, Object> response = restTemplate.postForObject(GEMINI_URL, request, Map.class);

            return extractText(response);
        } catch (RestClientException e) {
            // Gemini API 하루 사용 한도 초과(HTTP 429), 네트워크 오류 등 - 서비스 전체가 죽지 않고 안내 문구로 대체
            log.warn("Gemini API 호출 실패: {}", e.getMessage());
            return "지금은 AI 응답을 받아올 수 없어요. 잠시 후 다시 시도해주세요.";
        }
    }

    // Gemini의 응답은 { candidates: [ { content: { parts: [ { text: "..." } ] } } ] } 같은
    // 복잡한 중첩 구조로 옴. 그 안에서 우리가 진짜 필요한 텍스트 한 줄만 꺼내는 메서드
    @SuppressWarnings("unchecked") // Map<String,Object>를 강제로 형변환할 때 뜨는 경고를 무시함 (Gemini 응답 구조가 고정돼 있어서 안전함)
    private String extractText(Map<String, Object> response) {
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> firstCandidate = candidates.get(0);
        Map<String, Object> contentMap = (Map<String, Object>) firstCandidate.get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
        return (String) parts.get(0).get("text");
    }
}
