package megane6.weplanet.domain.dto.admin;

import java.time.LocalDateTime;

public record AdminPartnershipApplicationResponse(
		
		Long applicationId,
		
		String applicantTypeName,
		String applicantTypeLabel,
		
		String applicantName,
		String contactName,
		String email,
		String phone,
		String message,
		
		String statusName,
		String statusLabel,
		
		Long reviewerId,
		String reviewerNickname,
		LocalDateTime reviewedAt,
		
		String rejectionReason,
		
		LocalDateTime createdAt,
		LocalDateTime updatedAt

) {
}