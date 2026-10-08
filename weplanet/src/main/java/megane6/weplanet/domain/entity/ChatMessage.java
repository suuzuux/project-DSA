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

    // null 이면 방송 메시지, 값이 있으면 그 팬과의 개인 메시지
    @ManyToOne
    @JoinColumn(name = "fan_id")
    private User fan;

    // 실제로 보낸 사람 (아티스트 또는 팬)
    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 아티스트 화면 노출 여부 (도배 방지용, 전송 시 정한 값을 저장해 이력과 맞춤).
    @Builder.Default
    @Column(name = "visible_to_artist", nullable = false)
    private boolean visibleToArtist = true;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}