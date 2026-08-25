package megane6.weplanet.domain.entity.community;

import jakarta.persistence.*;
import lombok.*;
import megane6.weplanet.domain.entity.User;

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
	
	@ManyToOne
	@JoinColumn(name = "fan_id", nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private User fan;
	
	@ManyToOne
	@JoinColumn(name = "artist_id", nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private User artist;
	
	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;
	
	@PrePersist
	public void prePersist() {
		if (this.joinedAt == null) {
			this.joinedAt = LocalDateTime.now();
		}
	}
}