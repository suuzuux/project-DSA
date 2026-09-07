package megane6.weplanet.domain.entity.live;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.LiveSessionStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "live_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private User artist;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "host_id", nullable = false)
	private User host;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LiveSessionStatus status;

	@Column(name = "started_at", nullable = false, updatable = false)
	private LocalDateTime startedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	private LiveSession(User artist, User host) {
		this.artist = artist;
		this.host = host;
		this.status = LiveSessionStatus.LIVE;
	}

	public static LiveSession start(User artist, User host) {
		return new LiveSession(artist, host);
	}

	public void end() {
		if (this.status == LiveSessionStatus.ENDED) {
			return;
		}
		this.status = LiveSessionStatus.ENDED;
		this.endedAt = LocalDateTime.now();
	}

	public boolean isLive() {
		return this.status == LiveSessionStatus.LIVE;
	}

	public boolean isHost(User user) {
		return user != null && this.host.getId().equals(user.getId());
	}

	@PrePersist
	public void prePersist() {
		if (this.startedAt == null) {
			this.startedAt = LocalDateTime.now();
		}
		if (this.status == null) {
			this.status = LiveSessionStatus.LIVE;
		}
	}
}
