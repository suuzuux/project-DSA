package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    // 채팅 메시지 저장 - fan이 null이면 아티스트가 전체 팬에게 보낸 방송 메시지
    public ChatMessage saveMessage(User artist, User fan, User sender, String content) {
        ChatMessage message = ChatMessage.builder()
                .artist(artist)
                .fan(fan)
                .sender(sender)
                .content(content)
                .build();

        return chatMessageRepository.save(message);
    }
}