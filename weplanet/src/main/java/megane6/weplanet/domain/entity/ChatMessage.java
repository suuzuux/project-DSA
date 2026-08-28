package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 메시지가 속한 아티스트 채팅방
    @ManyToOne
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    // null이면 아티스트가 전체 팬에게 보낸 방송 메시지
    // 값이 있으면 그 팬 한 명과 아티스트만 주고받는 개인 메시지
    @ManyToOne
    @JoinColumn(name = "fan_id")
    private User fan;

    // 실제로 이 메시지를 보낸 사람 (아티스트 본인이거나, 특정 팬)
    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // CHAT-02 비대칭 수신: 팬이 보낸 메시지 중 일부만 아티스트 화면에 노출됨(도배 방지).
    // 노출 여부를 저장해두지 않으면 새로고침했을 때 히스토리에서 전부 다시 보여서
    // 도배 방지가 무의미해지므로, 전송 시점에 정해진 결과를 여기에 남겨둠.
    // (아티스트 본인이 보낸 방송은 항상 true)
    @Builder.Default
    @Column(name = "visible_to_artist", nullable = false)
    private boolean visibleToArtist = true;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}