package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 특정 아티스트 채팅방의 메시지 조회 (CHAT-02에서 본격 활용 예정)
    List<ChatMessage> findByArtistOrderByCreatedAtAsc(User artist);

    // DM 인박스 - 이 팬이 주고받은 메시지(자신의 개인 채널)를 최신순으로 조회.
    // 여기서 서비스단(ChatMessageService)이 아티스트별로 묶어서 "마지막 메시지"만 뽑아 씀
    List<ChatMessage> findByFanOrderByCreatedAtDesc(User fan);

    // 특정 아티스트-팬 사이의 1:1 대화 이력 (DM 방을 열 때 지난 메시지를 보여주기 위함)
    List<ChatMessage> findByArtistAndFanOrderByCreatedAtAsc(User artist, User fan);
}