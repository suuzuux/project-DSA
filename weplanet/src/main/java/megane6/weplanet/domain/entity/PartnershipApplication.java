package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicantType;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicationStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;
@Entity
@Table(name = "partnership_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnershipApplication {
	
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "applicant_type", nullable = false, length = 20)
	private PartnershipApplicantType applicantType;
	
	@Column(name = "applicant_name", nullable = false, length = 100)
	private String applicantName;
	
	@Column(name = "contact_name", nullable = false, length = 50)
	private String contactName;
	
	@Column(nullable = false, length = 150)
	private String email;
	
	@Column(length = 30)
	private String phone;
	
	@Column(nullable = false, length = 2000)
	private String message;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PartnershipApplicationStatus status;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by")
	private User reviewedBy;
	
	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;
	
	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	
	private PartnershipApplication(
			PartnershipApplicantType applicantType,
			String applicantName,
			String contactName,
			String email,
			String phone,
			String message
	) {
		if (applicantType == null) {
			throw new IllegalArgumentException("신청 유형을 선택해주세요.");
		}
		this.applicantType = applicantType;
		this.applicantName = requireText(applicantName, 100, "아티스트명 또는 소속사명을 입력해주세요.");
		this.contactName = requireText(contactName, 50, "담당자명을 입력해주세요.");
		this.email = normalizeEmail(email);
		this.phone = optionalText(phone, 30, "연락처는 30자 이하로 입력해주세요.");
		this.message = requireText(message, 2000, "신청 내용을 입력해주세요.");
		this.status = PartnershipApplicationStatus.PENDING_APPROVAL;
	}
	
	public static PartnershipApplication createPending(
			PartnershipApplicantType applicantType,
			String applicantName,
			String contactName,
			String email,
			String phone,
			String message
	) {
		return new PartnershipApplication(
				applicantType,
				applicantName,
				contactName,
				email,
				phone,
				message
		);
	}
	
	public void approve(User admin) {
		validatePendingReview(admin);
		
		this.status = PartnershipApplicationStatus.APPROVED;
		this.reviewedBy = admin;
		this.reviewedAt = LocalDateTime.now();
		this.rejectionReason = null;
	}
	
	public void reject(User admin, String reason) {
		validatePendingReview(admin);
		
		this.rejectionReason = requireText(reason, 500, "반려 사유를 입력해주세요.");
		this.status = PartnershipApplicationStatus.REJECTED;
		this.reviewedBy = admin;
		this.reviewedAt = LocalDateTime.now();
	}
	
	private void validatePendingReview(User admin) {
		if (admin == null || admin.getRole() != Role.ADMIN) {
			throw new IllegalStateException("관리자만 입점 신청을 처리할 수 있습니다.");
		}
		
		if (status != PartnershipApplicationStatus.PENDING_APPROVAL) {
			throw new IllegalStateException("검토 대기 중인 신청만 처리할 수 있습니다.");
		}
	}
	
	private String normalizeEmail(String value) {
		String normalized = requireText(value, 50, "이메일을 입력해주세요.").toLowerCase(Locale.ROOT);
		
		if (!EMAIL_PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("이메일 주소 형식을 확인해주세요.");
		}
		
		return normalized;
	}
	
	private String requireText(String value, int maxLength, String errorMessage) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(errorMessage);
		}
		
		String trimmed = value.trim();
		
		if (trimmed.length() > maxLength) {
			throw new IllegalArgumentException(errorMessage);
		}
		
		return trimmed;
	}
	
	private String optionalText(String value, int maxLength, String errorMessage) {
		if (value == null || value.isBlank()) {
			return null;
		}
		
		String trimmed = value.trim();
		
		if (trimmed.length() > maxLength) {
			throw new IllegalArgumentException(errorMessage);
		}
		
		return trimmed;
	}
	
	@PrePersist
	private void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}
	
	@PreUpdate
	private void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
