package megane6.weplanet.domain.dto.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminArtistResponse(
		Long userId,
		String username,
		String nickname,
		String email,
		
		String stageName,
		LocalDate debutDate,
		String position,
		String profileImg,
		
		String userStatusName,
		String userStatusLabel,
		
		Long agencyId,
		String agencyName,
		
		String agencyStatusName,
		String agencyStatusLabel,
		
		boolean emailVerified,
		
		LocalDateTime createdAt,
		LocalDateTime lastLoginAt
) {
}