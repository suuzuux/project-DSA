package megane6.weplanet.domain.entity.community;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 커뮤니티 가입과 커뮤니티 전용 프로필 (닉네임 10자, 소개 30자).
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

	// 가입 시각 = 프로필 생성 시각 (디데이 기준)
	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	// 커뮤니티별 프로필

	@Column(nullable = false, length = 10)
	private String nickname;

	@Column(length = 30)
	private String bio;

	@Column(name = "avatar_stored_name")
	private String avatarStoredName;

	@Column(name = "background_stored_name")
	private String backgroundStoredName;

	@Builder.Default
	@Column(name = "content_hidden", nullable = false)
	private boolean contentHidden = false;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	public void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		if (this.joinedAt == null) {
			this.joinedAt = now;
		}
		this.updatedAt = now;
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
