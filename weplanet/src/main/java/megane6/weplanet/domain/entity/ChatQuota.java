package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    @Column(nullable = false)
    private int remainingCount;

    @Column(nullable = false)
    private LocalDate chargedDate;
}