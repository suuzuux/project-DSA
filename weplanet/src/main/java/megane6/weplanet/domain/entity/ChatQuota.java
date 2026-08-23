package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * "이 팬이 이 아티스트에게 오늘 메시지를 몇 개 더 보낼 수 있는지"를 기록하는 엔티티.
 * (fan_id, artist_id) 조합마다 딱 하나씩만 존재함 - 팬 한 명이 아티스트 여러 명과 각각 채팅해도
 * 각 아티스트별로 한도가 따로 관리되도록 하기 위함.
 */
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

    // 오늘 더 보낼 수 있는 메시지 개수 (메시지 보낼 때마다 1씩 줄어듦)
    @Column(nullable = false)
    private int remainingCount;

    // 마지막으로 한도를 다시 채워준 날짜. 오늘 날짜와 다르면 "하루가 지났다"고 판단해서
    // ChatQuotaService가 remainingCount를 다시 최대치로 채워줌
    @Column(nullable = false)
    private LocalDate chargedDate;
}
