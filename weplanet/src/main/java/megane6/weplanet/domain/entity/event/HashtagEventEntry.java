package megane6.weplanet.domain.entity.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.enumfolder.events.HashtagEntryStatus;

import java.time.LocalDateTime;

/** 해시태그 글 1개의 집계 기록 (제외 사유 포함, 글이 삭제되면 함께 삭제). */
@Entity
@Table(name = "hashtag_event_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HashtagEventEntry {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_id", nullable = false)
	private HashtagEventTarget target;
	
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id", nullable = false)
	private Post post;
	
	// 참여 인원·하루 3건 계산에 바로 쓰려고 id 로 둔다.
	@Column(name = "fan_id", nullable = false)
	private Long fanId;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private HashtagEntryStatus status;
	
	// 글 작성 시각 ("하루 3건"의 기준)
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	public static HashtagEventEntry record(HashtagEventTarget target, Post post, HashtagEntryStatus status) {
		if (target == null || post == null || post.getAuthor() == null || status == null) {
			throw new IllegalArgumentException("집계 기록에 필요한 정보가 없습니다.");
		}
		
		HashtagEventEntry entry = new HashtagEventEntry();
		entry.target = target;
		entry.post = post;
		entry.fanId = post.getAuthor().getId();
		entry.status = status;
		entry.createdAt = post.getCreatedAt();
		
		return entry;
	}
}
