package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Role;

import java.time.LocalDateTime;

@Entity
@Table(name = "agency_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgencyProfile {

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

	@Column(length = 50)
	private String department;

	@Column(length = 50)
	private String position;

	@Column(name = "is_owner", nullable = false)
	private boolean owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approved_by")
	private User approvedBy;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;
	
	public boolean isApproved() {
		return approvedAt != null;
	}
	
	public void approve(User admin) {
		requireAdmin(admin);
		
		if (isApproved()) {
			throw new IllegalStateException("admin.error.agency.alreadyApproved");
		}
		this.approvedBy = admin;
		this.approvedAt = LocalDateTime.now();
	}
	
	public void revokeApproval(User admin) {
		requireAdmin(admin);
		
		if (!isApproved()) {
			throw new IllegalStateException("admin.error.agency.notApproved");
		}
		this.approvedBy = null;
		this.approvedAt = null;
	}
	
	private void requireAdmin(User admin) {
		if (admin == null || admin.getRole() != Role.ADMIN) {
			throw new IllegalArgumentException("admin.error.agency.adminOnlyChange");
		}
	}
}
