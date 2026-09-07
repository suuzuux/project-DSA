package megane6.weplanet.domain.dto.admin;

import java.time.LocalDateTime;

public record AdminAgencyPermissionResponse(
		Long userId,
		String username,
		String nickname,
		String email,
		
		String userStatusName,
		String userStatusLabel,
		
		Long agencyId,
		String agencyName,
		String businessNo,
		String ceoName,
		
		String agencyStatusName,
		String agencyStatusLabel,
		
		String department,
		String position,
		boolean owner,
		
		boolean approved,
		Long approvedById,
		String approvedByNickname,
		LocalDateTime approvedAt,
		
		LocalDateTime requestedAt
) {
}