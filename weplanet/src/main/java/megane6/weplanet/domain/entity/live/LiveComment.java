package megane6.weplanet.domain.entity.live;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "live_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	private LiveSession session;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;

	@Column(nullable = false, length = 500)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LiveComment(LiveSession session, User author, String content) {
		this.session = session;
		this.author = author;
		this.content = content;
	}

	public static LiveComment create(LiveSession session, User author, String content) {
		return new LiveComment(session, author, content);
	}

	@PrePersist
	public void prePersist() {
		if (this.createdAt == null) {
			this.createdAt = LocalDateTime.now();
		}
	}
}
