package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ChatMessageRequest;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.ChatMessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // 채팅방 테스트 화면 이동
    @GetMapping("/chat/room")
    public String room(
            @RequestParam Long artistId,
            Model model
    ) {
        model.addAttribute("artistId", artistId);
        return "chatRoom";
    }

    // 브라우저가 /app/chat.send 로 보낸 메시지를 저장하고, 채팅방 구독자들에게 실시간 방송
    @MessageMapping("/chat.send")
    public void send(ChatMessageRequest request) {
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

        messagingTemplate.convertAndSend("/topic/chat." + artist.getId(), (Object) payload);
    }
}