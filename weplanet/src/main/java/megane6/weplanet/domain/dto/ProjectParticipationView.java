package megane6.weplanet.domain.dto;

import java.time.LocalDateTime;

public record ProjectParticipationView(
		Long contributionId,
		Long projectId,
		Long artistId,
		String projectTitle,
		Long amount,
		String paymentStatus,
		boolean anonymous,
		LocalDateTime participatedAt
) {
}
