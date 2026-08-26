package megane6.weplanet.domain.entity.community;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityProfile {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@OneToOne
	@JoinColumn(name = "community_member_id", nullable = false, unique = true)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private CommunityMember communityMember;
	
	@Column(nullable = false, length = 10)
	private String nickname;
	
	@Column(length = 30)
	private String bio;
	
	@Column(name = "avatar_stored_name", length = 255)
	private String avatarStoredName;
	
	@Column(name = "background_stored_name", length = 255)
	private String backgroundStoredName;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	
	@PrePersist
	public void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		if (this.createdAt == null) this.createdAt = now;
		this.updatedAt = now;
	}
	
	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}