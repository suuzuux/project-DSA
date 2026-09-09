package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.NoticeCategory;

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
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NoticeCategory category;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(nullable = false)
	private boolean published;
	
	@Column(name = "publish_at")
	private LocalDateTime publishAt;
	
	@Column(nullable = false)
	private boolean pinned;
	
	@Column(name = "pin_order")
	private Integer pinOrder;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	
	private SiteNotice(
			User author,
			NoticeCategory category,
			String title,
			String content,
			boolean published,
			LocalDateTime publishedAt
	) {
		this.author = author;
		this.category = category;
		this.title = title;
		this.content = content;
		this.published = published;
		this.publishAt = publishedAt;
	}
	
	public static SiteNotice create(
			User author,
			NoticeCategory category,
			String title,
			String content,
			boolean published,
			LocalDateTime publishedAt
	) {
		return new SiteNotice(author, category, title.trim(), content.trim(), published, publishedAt);
	}
	
	public void applyPin(boolean pinned, Integer pinOrder) {
		this.pinned = pinned;
		this.pinOrder = pinOrder;
	}
	
	public void update(
			NoticeCategory category,
			String title,
			String content,
			boolean published,
			LocalDateTime publishedAt
	) {
		this.category = category;
		this.title = title.trim();
		this.content = content.trim();
		this.published = published;
		this.publishAt = publishedAt;
	}
	
	// published = true여도, publishedAt이 미래 시각이면 아직 비공개로 취급
	public boolean isVisible() {
		if (!published) {
			return false;
		}
		return publishAt == null || !publishAt.isAfter(LocalDateTime.now());
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
