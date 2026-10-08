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

    // 아티스트 채팅방 메시지 조회
    List<ChatMessage> findByArtistOrderByCreatedAtAsc(User artist);

    // DM 인박스 - 이 팬의 메시지 최신순 (아티스트별 마지막 메시지는 서비스에서 추림).
    List<ChatMessage> findByFanOrderByCreatedAtDesc(User fan);

    // 아티스트-팬 1:1 대화 이력
    List<ChatMessage> findByArtistAndFanOrderByCreatedAtAsc(User artist, User fan);

    // DM 방 전체 이력 - 1:1 메시지와 아티스트 방송(fan IS NULL)을 함께 조회.
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.artist = :artist AND (m.fan = :fan OR m.fan IS NULL)
            ORDER BY m.createdAt ASC
            """)
    List<ChatMessage> findConversationWithBroadcast(@Param("artist") User artist, @Param("fan") User fan);

    // 안 읽은 DM 개수용 - 방 주인이 직접 보낸 메시지만 (방송·개인 채널 포함).
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