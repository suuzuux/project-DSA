package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "group_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMember {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id", nullable = false)
	private ArtistGroup group;
	
	// artist_id 컬럼은 DB상 FK가 artist_profiles(user_id)를 가리키지만,
	// artist_profiles.user_id 자체가 users.id를 그대로 쓰는 1:1 구조라서 값은 결국 users.id와 동일함.
	// 그래서 User 엔티티로 바로 매핑해도 값 기준으로는 정확히 맞음.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private User artist;
	
	@Column(name = "is_leader", nullable = false)
	private boolean leader;
	
	@Column(name = "joined_at", nullable = false)
	private LocalDate joinedAt;
	
	@Column(name = "left_at")
	private LocalDate leftAt;
}