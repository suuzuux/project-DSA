package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.AdminLevel;

@Entity
@Table(name = "admin_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminProfile {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "admin_level", nullable = false, length = 20)
	private AdminLevel adminLevel;

	@Column(length = 50)
	private String department;

	@Column(name = "employee_no", unique = true, length = 30)
	private String employeeNo;
}
