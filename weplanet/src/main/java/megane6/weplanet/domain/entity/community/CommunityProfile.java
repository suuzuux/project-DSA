package megane6.weplanet.domain.entity.community;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// docs/260826_SQL.sql의 community_profiles 그대로 매핑.
// nickname varchar(10), bio varchar(30) - 스키마 길이 제한 그대로 반영.
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
	private CommunityMember communityMember;
	
	@Column(nullable = false, length = 10)
	private String nickname;
	
	@Column(length = 30)
	private String bio;
	
	@Column(name = "avatar_stored_name")
	private String avatarStoredName;
	
	@Column(name = "background_stored_name")
	private String backgroundStoredName;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	
	@PrePersist
	public void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}
	
	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}