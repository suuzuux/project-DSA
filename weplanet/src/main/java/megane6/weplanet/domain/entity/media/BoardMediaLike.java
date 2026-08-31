package megane6.weplanet.domain.entity.media;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(
		name = "board_media_like",
		uniqueConstraints = @UniqueConstraint(columnNames = {"board_id", "user_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardMediaLike {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "board_id", nullable = false)
	private BoardMediaEntity board;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
	}
}
