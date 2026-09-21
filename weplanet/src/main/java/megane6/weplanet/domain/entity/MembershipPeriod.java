package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "membership_period")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPeriod {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(name = "fan_id", nullable = false)
	private Long fanId;
	
	@Column(name = "artist_id", nullable = false)
	private Long artistId;
	
	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;
	
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;
	
	// 이 기간이 연속 몇 번째인지. 1 = 첫 가입(또는 끊겼다가 새로 시작)
	@Column(name = "streak_count", nullable = false)
	private int streakCount;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@PrePersist
	public void prePersist() {
		if (this.createdAt == null) {
			this.createdAt = LocalDateTime.now();
		}
	}
}
