package megane6.weplanet.domain.entity.event;

import megane6.weplanet.exception.LocalizedIllegalArgumentException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;

import java.util.regex.Pattern;

/**
 * 이벤트에 참여하는 아티스트(커뮤니티) 1팀과, 그 팀 팬들이 쓸 해시태그. (hashtag_event_target)
 * final_* 컬럼은 집계 확정 전엔 null, 확정하면 그 순간의 숫자가 고정된다.
 * (가입/탈퇴는 이벤트 후에도 계속되므로, 고정하지 않으면 공지한 참여율과 페이지 숫자가 달라짐)
 */
@Entity
@Table(name = "hashtag_event_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HashtagEventTarget {
	
	// # 다음에 한글·영문·숫자·밑줄(_)만. 띄어쓰기나 다른 기호가 끼면 SNS에서도 태그가 거기서 끊긴다
	private static final Pattern HASHTAG_PATTERN = Pattern.compile("^#[\\p{L}\\p{N}_]{1,99}$");
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "event_id", nullable = false)
	private HashtagEvent event;
	
	// 커뮤니티 = 아티스트(그룹) 계정 users.id
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private User artist;
	
	@Column(nullable = false, length = 100)
	private String hashtag;
	
	@Column(name = "final_member_count")
	private Integer finalMemberCount;
	
	@Column(name = "final_participant_count")
	private Integer finalParticipantCount;
	
	@Column(name = "final_post_count")
	private Integer finalPostCount;
	
	@Column(name = "final_rank")
	private Integer finalRank;
	
	// HashtagEvent.putTarget 에서만 만든다 (같은 패키지라 public 이 아니어도 됨)
	static HashtagEventTarget create(HashtagEvent event, User artist, String hashtag) {
		if (artist == null) {
			throw new IllegalArgumentException("adminHashtag.error.artistRequired");
		}
		
		HashtagEventTarget target = new HashtagEventTarget();
		target.event = event;
		target.artist = artist;
		target.hashtag = normalize(hashtag);
		return target;
	}
	
	void changeHashtag(String hashtag) {
		this.hashtag = normalize(hashtag);
	}
	
	public void recordFinalResult(int memberCount, int participantCount, int postCount, int rank) {
		this.finalMemberCount = memberCount;
		this.finalParticipantCount = participantCount;
		this.finalPostCount = postCount;
		this.finalRank = rank;
	}
	
	// "가을총공_STELLA", " #가을총공_STELLA " → "#가을총공_STELLA"
	public static String normalize(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("adminHashtag.error.hashtagEmpty");
		}
		
		String tag = raw.strip();
		if (!tag.startsWith("#")) {
			tag = "#" + tag;
		}
		
		if (!HASHTAG_PATTERN.matcher(tag).matches()) {
			throw new LocalizedIllegalArgumentException("adminHashtag.error.hashtagInvalid", raw);
		}
		
		return tag;
	}
}