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

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}