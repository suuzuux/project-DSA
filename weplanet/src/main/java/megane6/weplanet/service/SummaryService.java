package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final GeminiClient geminiClient;

    // 게시글 내용을 Gemini API에 보내 3줄 요약을 받아옴
    public String summarize(String content) {
        String prompt = "다음 글을 한국어로 3줄 이내로 간단히 요약해줘. 요약문 외에 다른 말은 하지 마.\n\n" + content;
        return geminiClient.generate(prompt);
    }
}
