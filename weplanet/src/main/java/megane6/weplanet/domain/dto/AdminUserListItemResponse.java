package megane6.weplanet.domain.dto;

import java.time.LocalDateTime;

public record AdminUserListItemResponse(
		Long userId,
		String username,
		String nickname,
		String email,
		
		String roleName,
		String roleLabel,
		
		String statusName,
		String statusLabel,
		
		String providerName,
		String providerLabel,
		
		Long agencyId,
		String agencyName,
		
		boolean emailVerified,
		
		LocalDateTime createdAt,
		LocalDateTime lastLoginAt
) {
}