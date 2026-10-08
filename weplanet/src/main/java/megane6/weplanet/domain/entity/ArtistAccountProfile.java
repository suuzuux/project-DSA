package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "artist_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistAccountProfile {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "user_id")
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "agency_id", nullable = false)
	private Agency agency;

	@Column(name = "stage_name", nullable = false, length = 50)
	private String stageName;

	@Column(name = "debut_date")
	private LocalDate debutDate;

	@Column(length = 50)
	private String position;

	@Column(columnDefinition = "TEXT")
	private String bio;

	@Column(name = "profile_img", length = 500)
	private String profileImg;
	
	private ArtistAccountProfile(
			User user,
			Agency agency,
			String stageName,
			LocalDate debutDate
	) {
		this.user = user;
		this.agency = agency;
		this.stageName = stageName;
		this.debutDate = debutDate;
	}
	
	// 그룹·멤버 계정 모두 이 행이 필요하다 (group_members FK).
	public static ArtistAccountProfile create(
			User user,
			Agency agency,
			String stageName,
			LocalDate debutDate
	) {
		if (user == null || agency == null) {
			throw new IllegalArgumentException("아티스트 계정과 소속사가 필요합니다.");
		}
		
		if (stageName == null || stageName.isBlank()) {
			throw new IllegalArgumentException("error.artistAccount.stageNameRequired");
		}
		
		return new ArtistAccountProfile(user, agency, stageName.trim(), debutDate);
	}
}
