package megane6.weplanet.domain.entity.community;

import jakarta.persistence.*;
import lombok.*;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "artist_group_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArtistGroupProfile {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(name = "artist_id", nullable = false, unique = true)
	private Long artistId;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", length = 10)
	private GroupGender gender;
	
	@Column(name = "member_count")
	private Integer memberCount;
	
	@Column(name = "nationality", length = 50)
	private String nationality;
	
	@Column(name = "category", length = 50)
	private String category;
	
	@Column(name = "debut_date")
	private LocalDate debutDate;
	
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	
	@PrePersist
	@PreUpdate
	public void touch() {
		this.updatedAt = LocalDateTime.now();
	}
}