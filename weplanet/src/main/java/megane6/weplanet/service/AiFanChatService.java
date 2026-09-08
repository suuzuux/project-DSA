package megane6.weplanet.service;

import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiFanChatService {

    private final GeminiClient geminiClient;
    private final UserRepository userRepository;
    private final ChatMessageService chatMessageService;
    private final ChatFilterService chatFilterService;
    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    // 실제 서비스 기능이 아닌 시연용 - 채팅방이 한산할 때 보여줄 가짜 팬 응원 메시지 생성
    public String generateFanMessage() {
        String prompt = "너는 K-pop 아이돌의 팬이야. 아티스트에게 짧고 애정 어린 응원 메시지를 한국어로 한 문장만 작성해줘. 메시지 외에 다른 말은 하지 마.";
        return geminiClient.generate(prompt);
    }

    /**
     * 아티스트가 DM을 보낸 뒤, 가상 팬 5명이 그 내용에 맞춰 차례로 답장한다.
     * 웹소켓 요청 스레드를 붙잡지 않도록 백그라운드에서 돈다.
     */
    @Async("aiFanExecutor")
    public void replyToArtistDm(Long artistId, String artistMessage) {
        User artist = userRepository.findById(artistId).orElse(null);
        if (artist == null) {
            log.warn("AI 팬 답장 중단: 아티스트 id={} 없음", artistId);
            return;
        }

        Map<String, User> fansByUsername = new HashMap<>();
        for (User fan : userRepository.findByUsernameIn(AiFanPersona.usernames())) {
            fansByUsername.put(fan.getUsername(), fan);
        }

        List<String> generated = generateReplies(artist.getNickname(), artistMessage);

        for (int i = 0; i < AiFanPersona.ALL.size(); i++) {
            AiFanPersona persona = AiFanPersona.ALL.get(i);
            User fan = fansByUsername.get(persona.username());
            if (fan == null) {
                log.warn("AI 팬 계정 없음: {}", persona.username());
                continue;
            }

            String content = pickContent(generated, i, persona);
            if (!isUsableReply(content) || chatFilterService.containsBannedWord(content)) {
                content = persona.fallbacks().get(ThreadLocalRandom.current().nextInt(persona.fallbacks().size()));
            }

            try {
                ChatMessage saved = chatMessageService.saveMessage(artist, fan, fan, content, true);
                Map<String, Object> payload = new HashMap<>();
                payload.put("senderId", fan.getId());
                payload.put("senderNickname", fan.getNickname());
                payload.put("fanId", fan.getId());
                payload.put("content", saved.getContent());
                payload.put("createdAt", saved.getCreatedAt().toString());

                messagingTemplate.convertAndSend("/topic/chat." + artist.getId() + ".artistFeed", (Object) payload);
            } catch (RuntimeException e) {
                log.warn("AI 팬 답장 저장/전송 실패 ({}): {}", persona.nickname(), e.getMessage());
            }

            sleepBeforeNext(i);
        }
    }

    private List<String> generateReplies(String artistNickname, String artistMessage) {
        String prompt = buildPrompt(artistNickname, artistMessage);
        String raw = geminiClient.generateJson(prompt);
        return parseReplies(raw);
    }

    private String buildPrompt(String artistNickname, String artistMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 K-pop 아이돌의 팬 5명이다. 아티스트 닉네임은 \"").append(artistNickname).append("\"이다.\n");
        if (artistMessage == null || artistMessage.isBlank()) {
            sb.append("아티스트가 아직 말을 걸기 전이다. 각자 먼저 짧게 인사/응원해라.\n");
        } else {
            sb.append("아티스트가 방금 DM으로 이렇게 말했다: \"").append(artistMessage.strip()).append("\"\n");
            sb.append("이 말에 대한 답장을 각자 한 문장씩 써라. 질문이면 질문에 답하고, 인사면 인사로 받아라.\n");
        }
        sb.append("규칙: 한국어만. 메시지 본문만. 다른 팬과 비슷한 문장을 쓰지 마라. 50자 이내.\n");
        sb.append("팬 설정:\n");
        for (int i = 0; i < AiFanPersona.ALL.size(); i++) {
            AiFanPersona p = AiFanPersona.ALL.get(i);
            sb.append(i + 1).append(". ").append(p.nickname()).append(" — ").append(p.personality()).append('\n');
        }
        sb.append("아래 JSON 배열만 출력해. 키는 nickname, content 이다. 닉네임은 위와 동일해야 한다.");
        return sb.toString();
    }

    private List<String> parseReplies(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String json = stripMarkdownFence(raw.strip());
        try {
            var root = jsonMapper.readTree(json);
            var array = root.isArray() ? root : root.get("replies");
            if (array == null || !array.isArray()) {
                return List.of();
            }
            List<String> contents = new ArrayList<>();
            array.forEach(node -> {
                var content = node.path("content").asText("");
                contents.add(content.strip());
            });
            return contents;
        } catch (Exception e) {
            log.warn("AI 팬 답장 JSON 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private static String stripMarkdownFence(String raw) {
        String trimmed = raw;
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl >= 0 && lastFence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, lastFence).strip();
            }
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static String pickContent(List<String> generated, int index, AiFanPersona persona) {
        if (generated.size() > index) {
            String fromAi = generated.get(index);
            if (isUsableReply(fromAi)) {
                return fromAi;
            }
        }
        return persona.fallbacks().get(ThreadLocalRandom.current().nextInt(persona.fallbacks().size()));
    }

    private static boolean isUsableReply(String content) {
        return content != null && !content.isBlank() && !content.contains("지금은 AI 응답");
    }

    private static void sleepBeforeNext(int index) {
        if (index >= AiFanPersona.ALL.size() - 1) {
            return;
        }
        try {
            Thread.sleep(700L + ThreadLocalRandom.current().nextLong(900));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
