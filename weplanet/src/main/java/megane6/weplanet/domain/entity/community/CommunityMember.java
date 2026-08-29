package megane6.weplanet.domain.entity.community;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_members", uniqueConstraints = @UniqueConstraint(columnNames = {"fan_id", "artist_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityMember {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(name = "fan_id", nullable = false)
	private Long fanId;
	
	@Column(name = "artist_id", nullable = false)
	private Long artistId;
	
	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;
	
	@PrePersist
	public void prePersist() {
		if (this.joinedAt == null) {
			this.joinedAt = LocalDateTime.now();
		}
	}
}