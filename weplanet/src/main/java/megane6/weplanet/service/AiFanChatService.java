package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiFanChatService {

    private final GeminiClient geminiClient;

    // 실제 서비스 기능이 아닌 시연용 - 채팅방이 한산할 때 보여줄 가짜 팬 응원 메시지 생성
    public String generateFanMessage() {
        String prompt = "너는 K-pop 아이돌의 팬이야. 아티스트에게 짧고 애정 어린 응원 메시지를 한국어로 한 문장만 작성해줘. 메시지 외에 다른 말은 하지 마.";
        return geminiClient.generate(prompt);
    }
}
