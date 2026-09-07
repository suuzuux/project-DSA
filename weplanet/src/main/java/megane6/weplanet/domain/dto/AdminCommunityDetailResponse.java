package megane6.weplanet.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminCommunityDetailResponse(
		AdminCommunityOverviewResponse overview,
		List<RecentPostItem> recentPosts,
		List<ProjectItem> recentProjects,
		List<BlockedMemberItem> blockedMembers,
		List<PendingReportItem> pendingReports
) {
	
	public record RecentPostItem(
			Long postId,
			String title,
			String boardTypeLabel,
			String authorNickname,
			int likeCount,
			LocalDateTime createdAt
	) {
	}
	
	public record ProjectItem(
			Long projectId,
			String title,
			String eventTypeLabel,
			String statusName,
			String statusLabel,
			Long goalAmount,
			LocalDateTime fundingEndAt
	) {
	}
	
	public record BlockedMemberItem(
			Long blockId,
			Long userId,
			String nickname,
			String username,
			String reason,
			LocalDateTime blockedAt
	) {
	}
	
	public record PendingReportItem(
			String targetType,
			Long targetId,
			String targetSummary,
			String reporterNickname,
			String reasonLabel,
			LocalDateTime reportedAt
	) {
	}
}