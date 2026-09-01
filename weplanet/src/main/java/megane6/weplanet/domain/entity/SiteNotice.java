package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "site_notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteNotice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private SiteNotice(User author, String title, String content, boolean published) {
		this.author = author;
		this.title = title;
		this.content = content;
		this.published = published;
	}

	public static SiteNotice create(User author, String title, String content, boolean published) {
		return new SiteNotice(author, title.trim(), content.trim(), published);
	}

	public void update(String title, String content, boolean published) {
		this.title = title.trim();
		this.content = content.trim();
		this.published = published;
	}

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
