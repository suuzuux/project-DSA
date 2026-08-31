package megane6.weplanet.domain.entity.calendar;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
		name = "artist_attendance",
		uniqueConstraints = @UniqueConstraint(columnNames = {"artist_id", "visit_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistAttendance {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private User artist;

	@Column(name = "visit_date", nullable = false)
	private LocalDate visitDate;

	@Column(name = "paw_color", nullable = false, length = 20)
	private String pawColor;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private ArtistAttendance(User artist, LocalDate visitDate, String pawColor) {
		this.artist = artist;
		this.visitDate = visitDate;
		this.pawColor = pawColor;
	}

	public static ArtistAttendance create(User artist, LocalDate visitDate, String pawColor) {
		return new ArtistAttendance(artist, visitDate, pawColor);
	}

	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
	}
}
