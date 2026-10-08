package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 팬·아티스트별 오늘 남은 메시지 전송 횟수. */
@Entity
@Table(
        name = "chat_quota",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fan_id", "artist_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fan_id", nullable = false)
    private User fan;

    @ManyToOne
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    // 오늘 남은 전송 횟수
    @Column(nullable = false)
    private int remainingCount;

    // 마지막으로 한도를 채운 날짜 (날짜가 바뀌면 다시 채움)
    @Column(nullable = false)
    private LocalDate chargedDate;
}
