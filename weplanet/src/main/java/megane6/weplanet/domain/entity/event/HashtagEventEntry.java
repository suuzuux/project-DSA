package megane6.weplanet.domain.entity.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.enumfolder.events.HashtagEntryStatus;

import java.time.LocalDateTime;

/**
 * 해시태그가 들어간 팬 게시글 1개의 집계 기록. (hashtag_event_entry)
 * 인정된 글(COUNTED)뿐 아니라 제외된 글도 사유와 함께 남긴다.
 * post_id 가 UNIQUE 라 글 하나는 한 번만 기록되고,
 * 글이 삭제되면 DB가 이 행도 같이 지운다(ON DELETE CASCADE) → 삭제된 글은 자동으로 집계에서 빠짐.
 */
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
	
	// 참여 인원(중복 없이)·1인 1일 3건을 셀 때 쓰므로 조인 없이 바로 쓰게 id 로 둔다 (CommunityMember.fanId 와 같은 방식)
	@Column(name = "fan_id", nullable = false)
	private Long fanId;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private HashtagEntryStatus status;
	
	// 글 작성 시각을 그대로 복사 ("하루 3건"의 하루 기준)
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
