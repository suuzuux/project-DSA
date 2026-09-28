package megane6.weplanet.domain.entity.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.events.HashtagEventStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 해시태그 총공 이벤트 1회분. (hashtag_event)
 * 기간은 날짜 단위로 받는다: 시작일 00:00:00 ~ 종료일 23:59:59, 3~7일.
 * 상태(예정/진행 중/종료/집계 확정)는 저장하지 않고 statusAt(지금 시각)으로 계산한다.
 */
@Entity
@Table(name = "hashtag_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HashtagEvent {
	
	public static final int MIN_DAYS = 3;
	public static final int MAX_DAYS = 7;
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false, length = 100)
	private String title;
	
	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;
	
	@Column(name = "end_at", nullable = false)
	private LocalDateTime endAt;
	
	// 관리자가 "집계 확정"을 누른 시각. null 이면 아직 확정 전
	@Column(name = "finalized_at")
	private LocalDateTime finalizedAt;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	
	// 참여 아티스트 목록.
	// cascade ALL : 이벤트를 저장/삭제하면 참여 아티스트도 같이 저장/삭제
	// orphanRemoval : 목록에서 빼기만 해도 DB 행이 지워짐
	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<HashtagEventTarget> targets = new ArrayList<>();
	
	public static HashtagEvent create(String title, LocalDate startDate, LocalDate endDate, User admin) {
		if (admin == null) {
			throw new IllegalArgumentException("관리자 정보가 필요합니다.");
		}
		
		HashtagEvent event = new HashtagEvent();
		event.createdBy = admin;
		event.changeInfo(title, startDate, endDate);
		return event;
	}
	
	// 시작 전에만 수정 가능 (진행 중에 기간·해시태그가 바뀌면 이미 쌓인 집계 기준이 흔들린다)
	public void update(String title, LocalDate startDate, LocalDate endDate, LocalDateTime now) {
		requireEditable(now);
		changeInfo(title, startDate, endDate);
	}
	
	public void requireEditable(LocalDateTime now) {
		if (!now.isBefore(startAt)) {
			throw new IllegalStateException("이미 시작된 이벤트는 수정할 수 없습니다.");
		}
	}
	
	// 참여 아티스트 추가 또는 해시태그 변경.
	// (전부 지우고 다시 넣으면 같은 아티스트가 DELETE보다 INSERT가 먼저 실행돼 UNIQUE 충돌이 나서, 있는 건 고쳐 쓴다)
	public void putTarget(User artist, String hashtag) {
		for (HashtagEventTarget target : targets) {
			if (target.getArtist().getId().equals(artist.getId())) {
				target.changeHashtag(hashtag);
				return;
			}
		}
		
		targets.add(HashtagEventTarget.create(this, artist, hashtag));
	}
	
	// 수정 폼에서 체크가 풀린 아티스트는 목록에서 뺀다 (orphanRemoval 로 DB 행도 삭제)
	public void retainTargets(Collection<Long> artistIds) {
		targets.removeIf(target -> !artistIds.contains(target.getArtist().getId()));
	}
	
	public HashtagEventStatus statusAt(LocalDateTime now) {
		if (finalizedAt != null) {
			return HashtagEventStatus.FINALIZED;
		}
		if (now.isBefore(startAt)) {
			return HashtagEventStatus.SCHEDULED;
		}
		if (!now.isAfter(endAt)) {
			return HashtagEventStatus.ONGOING;
		}
		return HashtagEventStatus.ENDED;
	}
	
	// 글 작성 시각이 이벤트 기간 안인지 (3단계 집계에서 사용)
	public boolean isOngoingAt(LocalDateTime time) {
		return !time.isBefore(startAt) && !time.isAfter(endAt);
	}
	
	// 종료 후 "집계 확정" 버튼. 아티스트별 숫자 고정은 서비스에서 target.recordFinalResult(...)로 한다
	public void finalizeResult(LocalDateTime now) {
		if (finalizedAt != null) {
			throw new IllegalStateException("이미 집계가 확정된 이벤트입니다.");
		}
		if (!now.isAfter(endAt)) {
			throw new IllegalStateException("이벤트가 끝난 뒤에 집계를 확정할 수 있습니다.");
		}
		
		this.finalizedAt = now;
	}
	
	public long periodDays() {
		return ChronoUnit.DAYS.between(startAt.toLocalDate(), endAt.toLocalDate()) + 1;
	}
	
	private void changeInfo(String title, LocalDate startDate, LocalDate endDate) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("이벤트명을 입력해주세요.");
		}
		if (title.strip().length() > 100) {
			throw new IllegalArgumentException("이벤트명은 100자까지 입력할 수 있습니다.");
		}
		if (startDate == null || endDate == null) {
			throw new IllegalArgumentException("이벤트 기간을 입력해주세요.");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
		}
		
		// 10.10 ~ 10.14 → 5일 (양 끝 날짜 포함)
		long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
		if (days < MIN_DAYS || days > MAX_DAYS) {
			throw new IllegalArgumentException(
					"이벤트 기간은 " + MIN_DAYS + "~" + MAX_DAYS + "일로 정해주세요. (현재 " + days + "일)");
		}
		
		this.title = title.strip();
		this.startAt = startDate.atStartOfDay();
		// LocalTime.MAX(23:59:59.999999999)는 MySQL이 반올림해서 다음 날 0시가 될 수 있어 초 단위로 끊는다
		this.endAt = endDate.atTime(23, 59, 59);
	}
	
	@PrePersist
	void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}
	
	@PreUpdate
	void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}