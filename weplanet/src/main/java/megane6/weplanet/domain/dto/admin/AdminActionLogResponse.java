package megane6.weplanet.domain.dto.admin;

import java.time.LocalDateTime;

public record AdminActionLogResponse(
		
		Long logId,
		
		Long actorId,
		String actorUsername,
		String actorNickname,
		
		String actionName,
		String actionLabel,
		
		String targetTypeName,
		String targetTypeLabel,
		Long targetId,
		
		String reason,
		String ipAddress,
		
		LocalDateTime createdAt

) {
}