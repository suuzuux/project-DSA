package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Language;
import org.springframework.stereotype.Service;

/**
 * 게시글/댓글 "번역보기" 기능 - SummaryService(AI 요약)와 완전히 같은 방식으로
 * GeminiClient를 재사용함 (별도 번역 API 키 없이, 이미 있는 gemini.api.key로 처리).
 * <p>
 * SETTINGS-02: 번역 대상 언어를 더 이상 "영어"로 고정하지 않는다. 호출하는 쪽(PostController)이
 * 로그인한 사용자의 "기본 서비스 언어"(User.preferredLanguage)를 그대로 넘겨줘서 프롬프트에 반영한다.
 */
@Service
@RequiredArgsConstructor
public class TranslateService {

    private final GeminiClient geminiClient;

    public String translate(String content, Language targetLanguage) {
        String prompt = "다음 글을 자연스러운 " + targetLanguage.displayNameKo() + "로 번역해줘. 번역문 외에 다른 말은 하지 마.\n\n" + content;
        return geminiClient.generate(prompt);
    }
}
