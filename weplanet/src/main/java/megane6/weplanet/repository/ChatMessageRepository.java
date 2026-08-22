package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 특정 아티스트 채팅방의 메시지 조회 (CHAT-02에서 본격 활용 예정)
    List<ChatMessage> findByArtistOrderByCreatedAtAsc(User artist);
}