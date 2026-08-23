package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ChatMessageRequest;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.AiFanChatService;
import megane6.weplanet.service.ChatFilterService;
import megane6.weplanet.service.ChatMessageService;
import megane6.weplanet.service.ChatQuotaService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatFilterService chatFilterService;
    private final ChatQuotaService chatQuotaService;
    private final AiFanChatService aiFanChatService;

    // 유저 조회 공통 헬퍼 - label은 에러 메시지에 쓸 대상 이름 ("아티스트", "팬" 등)
    private User getUserOrThrow(Long userId, String label) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(label + "를 찾을 수 없습니다."));
    }

    // 관리자 권한 체크 공통 헬퍼 - 관리자가 아니면 예외
    private void requireAdmin(User requester) {
        if (requester.getRole() != Role.ADMIN) {
            throw new IllegalStateException("관리자만 접근할 수 있습니다.");
        }
    }

    // 웹소켓 방송 공통 헬퍼 - payload를 (Object)로 캐스팅해서 넘기는 부분을 한 곳으로 모음
    private void broadcast(String destination, Map<String, Object> payload) {
        messagingTemplate.convertAndSend(destination, (Object) payload);
    }

    // 팬 전용 채팅방 화면 - 이 팬 개인 채널 + 아티스트 방송 채널만 구독
    @GetMapping("/chat/room/fan")
    public String fanRoom(
            @RequestParam Long artistId,
            @RequestParam Long fanId,
            Model model
    ) {
        User artist = getUserOrThrow(artistId, "아티스트");
        User fan = getUserOrThrow(fanId, "팬");

        model.addAttribute("artistId", artistId);
        model.addAttribute("fanId", fanId);
        model.addAttribute("remaining", chatQuotaService.getRemaining(fan, artist));

        return "fanChatRoom";
    }

    // 아티스트 전용 채팅방 화면 - 방송 채널 + 팬 메시지 중 랜덤으로 추려진 피드만 구독
    @GetMapping("/chat/room/artist")
    public String artistRoom(
            @RequestParam Long artistId,
            Model model
    ) {
        model.addAttribute("artistId", artistId);
        return "artistChatRoom";
    }

    // 브라우저가 /app/chat.send 로 보낸 메시지를 저장하고, 채팅방 구독자들에게 실시간 방송
    @MessageMapping("/chat.send")
    public void send(ChatMessageRequest request) {

        // 빈 메시지나 잘못된 요청은 조용히 무시 (금칙어 검사에서 content가 null이면 NPE 나는 것 방지)
        if (request.getContent() == null || request.getContent().isBlank()) {
            return;
        }

        // 금칙어가 포함되어 있으면 저장/방송하지 않고, 보낸 사람에게만 경고를 돌려줌
        if (chatFilterService.containsBannedWord(request.getContent())) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("error", true);
            warning.put("message", "부적절한 언어가 포함되어 전송이 제한되었습니다.");

            broadcast("/topic/chat.error." + request.getSenderId(), warning);

            return;
        }

        User artist = getUserOrThrow(request.getArtistId(), "아티스트");
        User sender = getUserOrThrow(request.getSenderId(), "보낸 사람");
        User fan = request.getFanId() != null
                ? getUserOrThrow(request.getFanId(), "팬")
                : null;

        if (fan != null && !chatQuotaService.tryConsume(fan, artist)) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("error", true);
            warning.put("message", "오늘 보낼 수 있는 메시지 횟수를 다 사용했습니다. 내일 다시 채워집니다.");

            broadcast("/topic/chat.error." + request.getSenderId(), warning);

            return;
        }

        ChatMessage saved = chatMessageService.saveMessage(artist, fan, sender, request.getContent());

        // 엔티티를 그대로 방송하지 않고, 화면에 필요한 값만 뽑아서 보냄 (User 안에 비밀번호 등 민감정보가 있어서)
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", sender.getId());
        payload.put("senderNickname", sender.getNickname());
        payload.put("fanId", fan != null ? fan.getId() : null);
        payload.put("content", saved.getContent());
        payload.put("createdAt", saved.getCreatedAt().toString());

        if (fan != null) {
            payload.put("remaining", chatQuotaService.getRemaining(fan, artist));
        }

        if (fan == null) {
            // 아티스트가 보낸 방송 메시지 - 아티스트 채널을 구독한 모든 팬에게 전달
            broadcast("/topic/chat." + artist.getId(), payload);
        } else {
            // 팬이 보낸 개인 메시지 - 일단 그 팬 개인 채널에는 무조건 전달
            broadcast("/topic/chat." + artist.getId() + ".fan." + fan.getId(), payload);

            // 도배 방지를 위해 30% 확률로만 아티스트의 추천 피드에도 노출
            if (Math.random() < 0.3) {
                broadcast("/topic/chat." + artist.getId() + ".artistFeed", payload);
            }
        }
    }

    // 금칙어 관리 화면 - 관리자만 접근 가능
    @GetMapping("/chat/admin/keywords")
    public String keywordList(
            @RequestParam(defaultValue = "3") Long testUserId,
            Model model
    ) {
        User requester = getUserOrThrow(testUserId, "테스트용 유저");
        requireAdmin(requester);

        model.addAttribute("keywords", chatFilterService.getAllKeywords());
        model.addAttribute("testUserId", testUserId);

        return "keywordManage";
    }

    // 금칙어 등록
    @PostMapping("/chat/admin/keywords")
    public String addKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "3") Long testUserId
    ) {
        User requester = getUserOrThrow(testUserId, "테스트용 유저");
        requireAdmin(requester);

        chatFilterService.addKeyword(keyword);

        return "redirect:/chat/admin/keywords?testUserId=" + testUserId;
    }

    // 금칙어 삭제
    @PostMapping("/chat/admin/keywords/{id}/delete")
    public String deleteKeyword(
            @PathVariable Long id,
            @RequestParam(defaultValue = "3") Long testUserId
    ) {
        User requester = getUserOrThrow(testUserId, "테스트용 유저");
        requireAdmin(requester);

        chatFilterService.deleteKeyword(id);

        return "redirect:/chat/admin/keywords?testUserId=" + testUserId;
    }

    // AI 팬 메시지 생성 (시연용) - 실제 팬이 아니라 아티스트 화면을 채우기 위한 가짜 메시지, 비동기 처리
    @PostMapping("/chat/room/artist/ai-fan")
    @ResponseBody
    public Map<String, Object> generateAiFan(@RequestParam Long artistId) {
        User artist = getUserOrThrow(artistId, "아티스트");
        User aiFan = userRepository.findByUsername("aifan_bot")
                .orElseThrow(() -> new IllegalStateException("AI 팬 계정(username=aifan_bot)이 없습니다."));

        String content = aiFanChatService.generateFanMessage();

        ChatMessage saved = chatMessageService.saveMessage(artist, aiFan, aiFan, content);

        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", aiFan.getId());
        payload.put("senderNickname", aiFan.getNickname());
        payload.put("fanId", aiFan.getId());
        payload.put("content", saved.getContent());
        payload.put("createdAt", saved.getCreatedAt().toString());

        broadcast("/topic/chat." + artist.getId() + ".artistFeed", payload);

        return Map.of("success", true);
    }
}
