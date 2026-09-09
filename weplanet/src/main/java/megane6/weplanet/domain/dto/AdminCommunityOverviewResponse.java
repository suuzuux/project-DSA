package megane6.weplanet.domain.dto;

public record AdminCommunityOverviewResponse(
		Long artistId,
		String artistNickname,
		String artistUsername,
		String statusName,
		String statusLabel,
		long memberCount,
		long postCount,
		long activeProjectCount,
		long blockedMemberCount,
		long pendingReportCount
) {
}