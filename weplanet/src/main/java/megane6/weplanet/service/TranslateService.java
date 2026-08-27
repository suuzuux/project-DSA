package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 게시글/댓글 "번역보기" 기능 - SummaryService(AI 요약)와 완전히 같은 방식으로
 * GeminiClient를 재사용함 (별도 번역 API 키 없이, 이미 있는 gemini.api.key로 처리).
 * <p>
 * 대상 언어는 헤더 🌐 에서 고른 weplanet_lang 쿠키를 따른다.
 */
@Service
@RequiredArgsConstructor
public class TranslateService {

    private static final Map<String, String> LANG_NAME = Map.of(
            "ko", "한국어",
            "en", "영어",
            "ja", "일본어",
            "zh", "중국어",
            "fr", "프랑스어",
            "es", "스페인어"
    );

    private final GeminiClient geminiClient;

    public String translate(String content) {
        return translate(content, "en");
    }

    public String translate(String content, String langCode) {
        String code = LANG_NAME.containsKey(langCode) ? langCode : "en";
        String lang = LANG_NAME.get(code);
        String prompt = "다음 글을 자연스러운 " + lang + "로 번역해줘. 번역문 외에 다른 말은 하지 마.\n\n" + content;
        return geminiClient.generate(prompt);
    }
}
