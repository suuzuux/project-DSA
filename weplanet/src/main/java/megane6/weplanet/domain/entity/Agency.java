package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.AgencyStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "agencies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agency {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String name;

	@Column(name = "business_no", unique = true, length = 20)
	private String businessNo;

	@Column(name = "ceo_name", length = 30)
	private String ceoName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AgencyStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private Agency(String name, String businessNo, String ceoName) {
		this.name = name;
		this.businessNo = businessNo;
		this.ceoName = ceoName;
		this.status = AgencyStatus.ACTIVE;
	}

	public static Agency create(String name, String businessNo, String ceoName) {
		return new Agency(name, businessNo, ceoName);
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
