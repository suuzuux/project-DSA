package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Language;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final GeminiClient geminiClient;

    // Gemini 로 게시글 3줄 요약
    public String summarize(String content) {
        return summarize(content, Language.KO);
    }

    // 로그인 사용자의 기본 서비스 언어로 요약한다.
    public String summarize(String content, Language targetLanguage) {
        Language language = targetLanguage != null ? targetLanguage : Language.KO;
        String prompt = "다음 글을 " + language.displayNameKo() + "로 3줄 이내로 간단히 요약해줘. 요약문 외에 다른 말은 하지 마.\n\n" + content;
        return geminiClient.generate(prompt);
    }
}
