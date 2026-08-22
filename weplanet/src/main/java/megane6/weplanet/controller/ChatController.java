package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ChatMessageRequest;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.ChatFilterService;
import megane6.weplanet.service.ChatMessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatFilterService chatFilterService;

    // 팬 전용 채팅방 화면 - 이 팬 개인 채널 + 아티스트 방송 채널만 구독
    @GetMapping("/chat/room/fan")
    public String fanRoom(
            @RequestParam Long artistId,
            @RequestParam Long fanId,
            Model model
    ) {
        model.addAttribute("artistId", artistId);
        model.addAttribute("fanId", fanId);
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

        // 금칙어가 포함되어 있으면 저장/방송하지 않고, 보낸 사람에게만 경고를 돌려줌
        if (chatFilterService.containsBannedWord(request.getContent())) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("error", true);
            warning.put("message", "부적절한 언어가 포함되어 전송이 제한되었습니다.");

            messagingTemplate.convertAndSend("/topic/chat.error." + request.getSenderId(), (Object) warning);

            return;
        }

        User artist = userRepository.findById(request.getArtistId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
        User sender = userRepository.findById(request.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("보낸 사람을 찾을 수 없습니다."));
        User fan = request.getFanId() != null
                ? userRepository.findById(request.getFanId())
                .orElseThrow(() -> new IllegalArgumentException("팬을 찾을 수 없습니다."))
                : null;

        ChatMessage saved = chatMessageService.saveMessage(artist, fan, sender, request.getContent());

        // 엔티티를 그대로 방송하지 않고, 화면에 필요한 값만 뽑아서 보냄 (User 안에 비밀번호 등 민감정보가 있어서)
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", sender.getId());
        payload.put("senderNickname", sender.getNickname());
        payload.put("fanId", fan != null ? fan.getId() : null);
        payload.put("content", saved.getContent());
        payload.put("createdAt", saved.getCreatedAt().toString());

        if (fan == null) {
            // 아티스트가 보낸 방송 메시지 - 아티스트 채널을 구독한 모든 팬에게 전달
            messagingTemplate.convertAndSend("/topic/chat." + artist.getId(), (Object) payload);
        } else {
            // 팬이 보낸 개인 메시지 - 일단 그 팬 개인 채널에는 무조건 전달
            messagingTemplate.convertAndSend("/topic/chat." + artist.getId() + ".fan." + fan.getId(), (Object) payload);

            // 도배 방지를 위해 30% 확률로만 아티스트의 추천 피드에도 노출
            if (Math.random() < 0.3) {
                messagingTemplate.convertAndSend("/topic/chat." + artist.getId() + ".artistFeed", (Object) payload);
            }
        }
    }

    // 금칙어 관리 화면 - 관리자만 접근 가능
    @GetMapping("/chat/admin/keywords")
    public String keywordList(
            @RequestParam(defaultValue = "3") Long testUserId,
            Model model
    ) {
        User requester = userRepository.findById(testUserId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + testUserId + ")가 없습니다."));

        if (requester.getRole() != Role.ADMIN) {
            throw new IllegalStateException("관리자만 접근할 수 있습니다.");
        }

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
        User requester = userRepository.findById(testUserId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + testUserId + ")가 없습니다."));

        if (requester.getRole() != Role.ADMIN) {
            throw new IllegalStateException("관리자만 접근할 수 있습니다.");
        }

        chatFilterService.addKeyword(keyword);

        return "redirect:/chat/admin/keywords?testUserId=" + testUserId;
    }

    // 금칙어 삭제
    @PostMapping("/chat/admin/keywords/{id}/delete")
    public String deleteKeyword(
            @PathVariable Long id,
            @RequestParam(defaultValue = "3") Long testUserId
    ) {
        User requester = userRepository.findById(testUserId)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=" + testUserId + ")가 없습니다."));

        if (requester.getRole() != Role.ADMIN) {
            throw new IllegalStateException("관리자만 접근할 수 있습니다.");
        }

        chatFilterService.deleteKeyword(id);

        return "redirect:/chat/admin/keywords?testUserId=" + testUserId;
    }
}