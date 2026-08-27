package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게시글/댓글 "번역보기" 기능 - SummaryService(AI 요약)와 완전히 같은 방식으로
 * GeminiClient를 재사용함 (별도 번역 API 키 없이, 이미 있는 gemini.api.key로 처리).
 * <p>
 * 참고: 아직 로그인/사용자별 언어 설정 기능이 없어서, "영어로 번역"으로 고정해뒀음.
 * 나중에 사용자 언어 설정이 생기면 그 값을 프롬프트에 반영하면 됨.
 */
@Service
@RequiredArgsConstructor
public class TranslateService {

    private final GeminiClient geminiClient;

    public String translate(String content) {
        String prompt = "다음 글을 자연스러운 영어로 번역해줘. 번역문 외에 다른 말은 하지 마.\n\n" + content;
        return geminiClient.generate(prompt);
    }
}
