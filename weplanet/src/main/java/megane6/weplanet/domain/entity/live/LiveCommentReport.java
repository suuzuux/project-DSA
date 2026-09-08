package megane6.weplanet.domain.entity.live;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;

import java.time.LocalDateTime;

@Entity
@Table(
		name = "live_comment_report",
		uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "reporter_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveCommentReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "comment_id", nullable = false)
	private LiveComment comment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reporter_id", nullable = false)
	private User reporter;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportReason reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	public void prePersist() {
		if (this.createdAt == null) {
			this.createdAt = LocalDateTime.now();
		}
	}
}
