package megane6.weplanet.service.main;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Language;
import org.springframework.stereotype.Service;

/** 게시글·댓글 번역보기 (GeminiClient 재사용, 대상 언어는 사용자 기본 서비스 언어). */
@Service
@RequiredArgsConstructor
public class TranslateService {

    private final GeminiClient geminiClient;

    public String translate(String content, Language targetLanguage) {
        String prompt = "다음 글을 자연스러운 " + targetLanguage.displayNameKo() + "로 번역해줘. 번역문 외에 다른 말은 하지 마.\n\n" + content;
        return geminiClient.generate(prompt);
    }
}
