package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 특정 아티스트 채팅방의 메시지 조회 (CHAT-02에서 본격 활용 예정)
    List<ChatMessage> findByArtistOrderByCreatedAtAsc(User artist);

    // DM 인박스 - 이 팬이 주고받은 메시지(자신의 개인 채널)를 최신순으로 조회.
    // 여기서 서비스단(ChatMessageService)이 아티스트별로 묶어서 "마지막 메시지"만 뽑아 씀
    List<ChatMessage> findByFanOrderByCreatedAtDesc(User fan);

    // 특정 아티스트-팬 사이의 1:1 대화 이력 (DM 방을 열 때 지난 메시지를 보여주기 위함)
    List<ChatMessage> findByArtistAndFanOrderByCreatedAtAsc(User artist, User fan);

    // DM 방에 표시할 전체 이력 - 1:1 개인 메시지 + 아티스트가 전체 팬에게 보낸 방송(fan IS NULL)을 함께 조회.
    // 방송 메시지는 fan 없이 저장되기 때문에 위 메서드로는 안 잡혀서,
    // 실시간으로는 보이던 아티스트 메시지가 새로고침하면 사라지는 문제가 있었음
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.artist = :artist AND (m.fan = :fan OR m.fan IS NULL)
            ORDER BY m.createdAt ASC
            """)
    List<ChatMessage> findConversationWithBroadcast(@Param("artist") User artist, @Param("fan") User fan);

    // 팬 화면 DM 안 읽은 개수용 - 방 주인(솔로 아티스트 또는 그룹 멤버)이 "직접" 보낸 메시지만 조회.
    // 전체 방송(fan IS NULL)과 이 팬 개인 채널 둘 다 포함하고, AI 가상 팬·다른 팬이 보낸 건 빼려고 sender = artist 로 거른다
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.artist.id IN :roomIds
              AND m.sender.id = m.artist.id
              AND (m.fan IS NULL OR m.fan = :fan)
              AND m.createdAt > :since
            ORDER BY m.createdAt ASC
            """)
    List<ChatMessage> findOwnerMessagesSince(@Param("roomIds") Collection<Long> roomIds,
                                             @Param("fan") User fan,
                                             @Param("since") LocalDateTime since);
}