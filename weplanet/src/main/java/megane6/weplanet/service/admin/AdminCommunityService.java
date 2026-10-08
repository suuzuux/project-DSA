package megane6.weplanet.service.admin;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.repository.comment.CommentReportRepository;
import megane6.weplanet.repository.fan.ReportRepository;
import megane6.weplanet.repository.project.ProjectRepository;
import megane6.weplanet.repository.fan.PostRepository;
import megane6.weplanet.repository.project.ProjectSettlementAccountRepository;
import megane6.weplanet.repository.project.ProjectContributionRepository;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.AdminCommunityDetailResponse;
import megane6.weplanet.domain.dto.AdminCommunityDetailResponse.BlockedMemberItem;
import megane6.weplanet.domain.dto.AdminCommunityDetailResponse.PendingReportItem;
import megane6.weplanet.domain.dto.AdminCommunityDetailResponse.ProjectItem;
import megane6.weplanet.domain.dto.AdminCommunityDetailResponse.RecentPostItem;
import megane6.weplanet.domain.dto.AdminCommunityOverviewResponse;
import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.dto.ProjectFundingSummary;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.ProjectSettlementAccount;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.*;
import megane6.weplanet.domain.entity.portal.ArtistBlock;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.portal.ArtistBlockRepository;
import megane6.weplanet.service.admin.AdminActionLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCommunityService {
	private final UserRepository ur;
	private final ProjectRepository pr;
	private final ArtistBlockRepository abr;
	private final ProjectContributionRepository pcr;
	private final ProjectSettlementAccountRepository psr;
	
	private final CommunityMemberRepository cmr;
	private final PostRepository postRepository;
	private final ReportRepository rr;
	private final CommentReportRepository crr;
	
	private final AdminActionLogService als;
	// 화면 표시용 라벨을 현재 로케일로 해석
	private final megane6.weplanet.i18n.Messages messages;
	
	public CommunityStats getStats() {
		long totalCommunityCount = ur.countByRole(Role.ARTIST);
		long pendingProjectCount = pr.countByStatusAndDeletedAtIsNull(
				FanProjectStatus.PENDING_APPROVAL);
		long fundingProjectCount = pr.countByStatusAndDeletedAtIsNull(
				FanProjectStatus.FUNDING);
		long restrictedMemberCount = abr.count();
		
		return new CommunityStats(
				totalCommunityCount,
				pendingProjectCount,
				fundingProjectCount,
				restrictedMemberCount
		);
	}
	
	public List<AdminCommunityOverviewResponse> getCommunityOverview(
			String keyword, UserStatus status, String sort) {
		String normalizedKeyword = keyword == null || keyword.isBlank()
				? null : keyword.trim();
		List<User> artists = ur.searchByRole(
				Role.ARTIST, status, normalizedKeyword);
		if (artists.isEmpty()) {
			return List.of();
		}
		
		List<Long> artistIds = artists.stream().map(User::getId).toList();
		Map<Long, Long> memberCounts = toCountMap(cmr.countMembersByArtistIds(artistIds));
		Map<Long, Long> postCounts = toCountMap(postRepository.countPostsByArtistIds(artistIds));
		Map<Long, Long> activeProjectCounts = toCountMap(pr.countProjectsByArtistIdsAndStatuses(
				artistIds, List.of(FanProjectStatus.APPROVED, FanProjectStatus.FUNDING)));
		Map<Long, Long> blockedMemberCounts = toCountMap(abr.countBlocksByArtistIds(artistIds));
		Map<Long, Long> pendingPostReportCounts = toCountMap(rr.countReportsByArtistIdsAndStatus(
				artistIds, ReportStatus.PENDING));
		Map<Long, Long> pendingCommentReportCounts = toCountMap(crr.countReportsByArtistIdsAndStatus(
				artistIds, ReportStatus.PENDING));
		
		Comparator<AdminCommunityOverviewResponse> comparator =
				communityOverviewComparator(sort);
		return artists.stream().map(artist -> {
			Long artistId = artist.getId();
			long pendingReportCount = pendingPostReportCounts.getOrDefault(artistId, 0L)
					+ pendingCommentReportCounts.getOrDefault(artistId, 0L);
					return new AdminCommunityOverviewResponse(
							artistId,
							artist.getNickname(),
							artist.getUsername(),
							artist.getStatus().name(),
							userStatusLabel(artist.getStatus()),
							memberCounts.getOrDefault(artistId, 0L),
							postCounts.getOrDefault(artistId, 0L),
							activeProjectCounts.getOrDefault(artistId, 0L),
							blockedMemberCounts.getOrDefault(artistId, 0L),
							pendingReportCount
					);
				})
				.sorted(comparator)
				.toList();
	}
	
	private Comparator<AdminCommunityOverviewResponse> communityOverviewComparator(String sort) {
		Comparator<AdminCommunityOverviewResponse> byName =
				Comparator.comparing(AdminCommunityOverviewResponse::artistNickname,
						String.CASE_INSENSITIVE_ORDER);
		if (sort == null) {
			return byName;
		}
		
		return switch (sort) {
			case "MEMBERS_DESC" -> Comparator.comparingLong(
					AdminCommunityOverviewResponse::memberCount)
					.reversed()
					.thenComparing(byName);
			case "POSTS_DESC" -> Comparator.comparingLong(
					AdminCommunityOverviewResponse::postCount)
					.reversed()
					.thenComparing(byName);
			case "BLOCKS_DESC" -> Comparator.comparingLong(
					AdminCommunityOverviewResponse::blockedMemberCount)
					.reversed()
					.thenComparing(byName);
			case "REPORTS_DESC" -> Comparator.comparingLong(
					AdminCommunityOverviewResponse::pendingReportCount)
					.reversed()
					.thenComparing(byName);
			default -> byName;
		};
	}
	
	public CommunityOverviewStats getCommunityOverviewStats(
			List<AdminCommunityOverviewResponse> communities
	) {
		long totalMemberCount = communities.stream()
				.mapToLong(AdminCommunityOverviewResponse::memberCount)
				.sum();
		long activeProjectCount = communities.stream()
				.mapToLong(AdminCommunityOverviewResponse::activeProjectCount)
				.sum();
		long blockedMemberCount = communities.stream()
				.mapToLong(AdminCommunityOverviewResponse::blockedMemberCount)
				.sum();
		long pendingReportCount = communities.stream()
				.mapToLong(AdminCommunityOverviewResponse::pendingReportCount)
				.sum();
		
		return new CommunityOverviewStats(
				communities.size(),
				totalMemberCount,
				activeProjectCount,
				blockedMemberCount,
				pendingReportCount
		);
	}
	
	public AdminCommunityDetailResponse getCommunityDetail(Long artistId) {
		User artist = ur.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("admin.error.community.notFound"));
		AdminCommunityOverviewResponse overview =
				getCommunityOverview(artist.getUsername(), null, "NAME_ASC")
						.stream()
						.filter(item -> item.artistId().equals(artistId))
						.findFirst()
						.orElseThrow(() -> new IllegalArgumentException("admin.error.community.overviewUnavailable"));
		
		List<RecentPostItem> recentPosts =
				postRepository
						.findTop10ByArtistOrderByCreatedAtDesc(artist)
						.stream()
						.map(post -> new RecentPostItem(
								post.getId(),
								post.getTitle(),
								boardTypeLabel(post.getBoardType()),
								post.getAuthor() == null
										? "-"
										: post.getAuthor().getNickname(),
								post.getLikeCount(),
								post.getCreatedAt()
						))
						.toList();
		
		List<ProjectItem> recentProjects =
				pr.findTop10ByArtistAndDeletedAtIsNullOrderByCreatedAtDesc(
								artist
						)
						.stream()
						.map(project -> new ProjectItem(
								project.getId(),
								project.getTitle(),
								messages.get("project.eventType." + project.getEventType().name()),
								project.getStatus().name(),
								messages.get("project.status." + project.getStatus().name()),
								project.getGoalAmount(),
								project.getFundingEndAt()
						))
						.toList();
		
		List<BlockedMemberItem> blockedMembers =
				abr.findByArtistOrderByCreatedAtDesc(artist)
						.stream()
						.limit(10)
						.map(block -> new BlockedMemberItem(
								block.getId(),
								block.getBlockedUser().getId(),
								block.getBlockedUser().getNickname(),
								block.getBlockedUser().getUsername(),
								block.getReason() == null
										? messages.get("admin.communityDetail.noReason")
										: block.getReason(),
								block.getCreatedAt()
						))
						.toList();
		
		List<PendingReportItem> postReports =
				rr.findTop10ByPost_ArtistAndStatusOrderByCreatedAtDesc(
								artist,
								ReportStatus.PENDING
						)
						.stream()
						.map(report -> new PendingReportItem(
								messages.get("admin.communityDetail.target.POST"),
								report.getPost().getId(),
								report.getPost().getTitle(),
								report.getReporter().getNickname(),
								reportReasonLabel(report.getReason()),
								report.getCreatedAt()
						))
						.toList();
		
		List<PendingReportItem> commentReports =
				crr.findTop10ByComment_Post_ArtistAndStatusOrderByCreatedAtDesc(
								artist,
								ReportStatus.PENDING
						)
						.stream()
						.map(report -> new PendingReportItem(
								messages.get("admin.communityDetail.target.COMMENT"),
								report.getComment().getId(),
								summarizeText(
										report.getComment().getContent()
								),
								report.getReporter().getNickname(),
								reportReasonLabel(report.getReason()),
								report.getCreatedAt()
						))
						.toList();
		
		Comparator<PendingReportItem> newestReportFirst =
				Comparator.comparing(
						PendingReportItem::reportedAt
				).reversed();
		
		List<PendingReportItem> pendingReports =
				Stream.concat(
								postReports.stream(),
								commentReports.stream()
						)
						.sorted(newestReportFirst)
						.limit(10)
						.toList();
		
		return new AdminCommunityDetailResponse(
				overview,
				recentPosts,
				recentProjects,
				blockedMembers,
				pendingReports
		);
	}
	
	@Transactional
	public void unblockMember(Long artistId,
							  Long blockId,
							  Long adminId,
							  String ipAddress) {
		User artist = ur.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() ->
						new IllegalArgumentException("admin.error.community.notFound"));
		
		ArtistBlock block = abr.findByIdAndArtist(blockId, artist)
				.orElseThrow(() -> new IllegalArgumentException("admin.error.community.blockNotFound"));
		
		User blockedUser = block.getBlockedUser();
		
		Long blockedUserId = blockedUser.getId();
		String blockedUserNickname = blockedUser.getNickname();
		
		abr.delete(block);
		als.recordAction(
				adminId,
				AdminActionType.COMMUNITY_MEMBER_UNBLOCK,
				AdminTargetType.USER,
				blockedUserId,
				"커뮤니티 차단 해제 : " + artist.getNickname() + " / " + blockedUserNickname,
				ipAddress);
	}
	
	private String boardTypeLabel(BoardType boardType) {
		return switch (boardType) {
			case FAN -> messages.get("admin.communityDetail.board.FAN");
			case ARTIST -> messages.get("admin.communityDetail.board.ARTIST");
		};
	}
	
	private String reportReasonLabel(ReportReason reason) {
		return switch (reason) {
			case SPAM -> messages.get("community.report.reason.SPAM");
			case ABUSE -> messages.get("community.report.reason.ABUSE");
			case SEXUAL -> messages.get("community.report.reason.SEXUAL");
			case ETC -> messages.get("community.report.reason.ETC");
		};
	}
	
	private String summarizeText(String text) {
		if (text == null || text.isBlank()) {
			return "-";
		}
		String trimmed = text.trim();
		if (trimmed.length() <= 40) {
			return trimmed;
		}
		
		return trimmed.substring(0, 40) + "…";
	}
	
	public List<PendingProjectItem> getPendingProjects() {
		return pr.findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
				FanProjectStatus.PENDING_APPROVAL)
				.stream()
				.map(this::toPendingProjectItem)
				.toList();
	}
	
	public List<AdminProjectItem> getAdminProjects(FanProjectStatus status, String keyword) {
		String normalizedKeyword = keyword == null || keyword.isBlank()
						? null : keyword.trim();
		
		List<Project> projects = pr.searchForAdmin(status, normalizedKeyword);
		
		if (projects.isEmpty()) {
			return List.of();
		}
		
		List<Long> projectIds = projects.stream()
				.map(Project::getId)
				.toList();
		
		Map<Long, ProjectFundingSummary> summaryMap = pcr.summarizePaidByProjectIds(
				projectIds, FanProjectPaymentStatus.PAID)
						.stream()
						.collect(Collectors.toMap(
								ProjectFundingSummary::projectId,
								summary -> summary
						));
		
		return projects.stream().map(project -> {
					ProjectFundingSummary summary =
							summaryMap.getOrDefault(
									project.getId(),
									new ProjectFundingSummary(
											project.getId(),
											0L,
											0L
									)
							);
					
					return new AdminProjectItem(
							project.getId(),
							project.getTitle(),
							messages.get("project.eventType." + project.getEventType().name()),
							project.getArtist().getNickname(),
							project.getCreator().getNickname(),
							project.getStatus().name(),
							messages.get("project.status." + project.getStatus().name()),
							project.getGoalAmount(),
							summary.fundedAmount(),
							summary.participantCount(),
							calculateProgress(
									summary.fundedAmount(),
									project.getGoalAmount()
							),
							project.getFundingStartAt(),
							project.getFundingEndAt(),
							project.getReviewedBy() == null
									? null
									: project.getReviewedBy().getNickname(),
							project.getReviewedAt(),
							project.getRejectionReason(),
							project.getCreatedAt()
					);
				})
				.toList();
	}
	
	public List<SettlementProjectItem> getSettlementProjects() {
		List<Project> projects = pr.searchForAdmin(FanProjectStatus.FUNDING_CLOSED, null);
		if (projects.isEmpty()) {
			return List.of();
		}
		
		List<Long> projectIds = projects.stream().map(Project::getId).toList();
		Map<Long, ProjectFundingSummary> summaryMap = pcr.summarizePaidByProjectIds(
				projectIds, FanProjectPaymentStatus.PAID)
				.stream()
				.collect(Collectors.toMap(
						ProjectFundingSummary::projectId,
						summary -> summary));
		
		Map<Long, ProjectSettlementAccount> accountMap = psr.findByProject_IdIn(projectIds)
				.stream()
				.collect(Collectors.toMap(
						account -> account.getProject().getId(),
						account -> account));
		
		return projects.stream().map(project -> {
			ProjectFundingSummary summary = summaryMap.getOrDefault(
					project.getId(), new ProjectFundingSummary(project.getId(), 0L, 0L)
			);
			ProjectSettlementAccount account = accountMap.get(project.getId());
			return new SettlementProjectItem(
					project.getId(),
					project.getTitle(),
					project.getArtist().getNickname(),
					project.getCreator().getNickname(),
					project.getGoalAmount(),
					summary.fundedAmount(),
					summary.participantCount(),
					calculateProgress(summary.fundedAmount(), project.getGoalAmount()),
					project.getFundingEndAt(),
					account == null ? "-" : messages.get("project.bank." + account.getBank().name()),
					account == null ? "-" : account.getAccountNumberLast4(),
					account == null ? "MISSING" : account.getVerificationStatus().name(),
					verificationLabel(account)
			);
		})
				.toList();
	}
	
	private String verificationLabel(ProjectSettlementAccount account) {
		if (account == null) {
			return messages.get("admin.communities.verification.MISSING");
		}
		return switch (account.getVerificationStatus()) {
			case UNVERIFIED -> messages.get("admin.communities.verification.UNVERIFIED");
			case VERIFIED -> messages.get("admin.communities.verification.VERIFIED");
			case FAILED -> messages.get("admin.communities.verification.FAILED");
		};
	}
	
	private int calculateProgress(long fundedAmount, long goalAmount) {
		if (goalAmount <= 0) {
			return 0;
		}
		return (int) Math.min(999, fundedAmount * 100 / goalAmount);
	}
	
	private PendingProjectItem toPendingProjectItem(Project project) {
		return new PendingProjectItem(
				project.getId(),
				project.getTitle(),
				messages.get("project.eventType." + project.getEventType().name()),
				project.getArtist().getNickname(),
				project.getCreator().getNickname(),
				project.getGoalAmount(),
				project.getFundingStartAt(),
				project.getFundingEndAt(),
				project.getCreatedAt()
		);
	}
	
	private Map<Long, Long> toCountMap(List<ArtistCount> counts) {
		return counts.stream()
				.collect(Collectors.toMap(
						ArtistCount::artistId,
						ArtistCount::count
				));
	}
	
	private String userStatusLabel(UserStatus status) {
		return switch (status) {
			case ACTIVE -> messages.get("admin.overview.status.ACTIVE");
			case DORMANT -> messages.get("admin.overview.status.DORMANT");
			case SUSPENDED -> messages.get("admin.overview.status.SUSPENDED");
			case WITHDRAWN -> messages.get("admin.overview.status.WITHDRAWN");
			case PENDING_ACTIVATION -> messages.get("admin.overview.status.PENDING_ACTIVATION");
		};
	}
	
	@Transactional
	public void verifySettlementAccount(
			Long projectId,
			Long adminId,
			String ipAddress) {
		User admin = getAdmin(adminId);
		Project project = getSettlementProject(projectId);
		ProjectSettlementAccount account = getSettlementAccount(projectId);
		
		account.verify(admin);
		
		als.recordAction(
				admin.getId(),
				AdminActionType.SETTLEMENT_ACCOUNT_VERIFY,
				AdminTargetType.SETTLEMENT,
				project.getId(),
				project.getTitle() + "정산 계좌 확인 완료",
				ipAddress);
	}
	
	@Transactional
	public void failSettlementAccountVerification(
			Long projectId,
			Long adminId,
			String ipAddress) {
		User admin = getAdmin(adminId);
		Project project = getSettlementProject(projectId);
		ProjectSettlementAccount account = getSettlementAccount(projectId);
		
		account.failVerification(admin);
		
		als.recordAction(
				admin.getId(),
				AdminActionType.SETTLEMENT_ACCOUNT_FAIL,
				AdminTargetType.SETTLEMENT,
				project.getId(),
				project.getTitle() + "정산 계좌 확인 실패",
				ipAddress);
	}
	
	@Transactional
	public void completeSettlement(
			Long projectId,
			Long adminId,
			String ipAddress) {
		User admin = getAdmin(adminId);
		Project project = getSettlementProject(projectId);
		ProjectSettlementAccount account = getSettlementAccount(projectId);
		
		if (account.getVerificationStatus() != SettlementVerificationStatus.VERIFIED) {
			throw new IllegalStateException("admin.error.settlement.accountNotVerified");
		}
		
		project.completeSettlement(admin);
		als.recordAction(
				admin.getId(),
				AdminActionType.SETTLEMENT_COMPLETE,
				AdminTargetType.SETTLEMENT,
				project.getId(),
				project.getTitle() + "정산 완료",
				ipAddress);
	}
	
	private User getAdmin(Long adminId) {
		return ur.findById(adminId).filter(user -> user.getRole() == Role.ADMIN)
				.orElseThrow(() -> new IllegalStateException("admin.error.settlement.adminOnly"));
	}
	
	private Project getSettlementProject(Long projectId) {
		Project project = pr.findById(projectId).orElseThrow(() ->
				new IllegalArgumentException("error.project.notFound"));
		if (project.getDeletedAt() != null) {
			throw new IllegalArgumentException("error.project.deleted");
		}
		if (project.getStatus() != FanProjectStatus.FUNDING_CLOSED) {
			throw new IllegalStateException("error.project.settleOnlyClosed");
		}
		return project;
	}
	
	private ProjectSettlementAccount getSettlementAccount(Long projectId) {
		return psr.findByProject_Id(projectId).orElseThrow(() ->
				new IllegalArgumentException("admin.error.settlement.accountMissing"));
	}
	
	public ProjectReviewDetail getProjectReviewDetail(Long projectId) {
		Project project = pr.findById(projectId).orElseThrow(() ->
				new IllegalArgumentException("error.project.notFound"));
		if (project.getDeletedAt() != null) {
			throw new IllegalArgumentException("error.project.deleted");
		}
		if (project.getStatus() != FanProjectStatus.PENDING_APPROVAL) {
			throw new IllegalArgumentException("admin.error.community.notPendingProject");
		}
		return new ProjectReviewDetail(
				project.getId(),
				project.getArtist().getId(),
				project.getTitle(),
				project.getDescription(),
				messages.get("project.eventType." + project.getEventType().name()),
				project.getArtist().getNickname(),
				project.getCreator().getNickname(),
				project.getGoalAmount(),
				project.getFundingStartAt(),
				project.getFundingEndAt(),
				project.getBasicBadgeCountAtApply(),
				project.getSpecialBadgeCountAtApply(),
				project.getIdentityVerifiedAt(),
				messages.get("project.status." + project.getStatus().name()),
				project.getCreatedAt()
		);
	}
	
	public record CommunityStats(
			long totalCommunityCount,
			long pendingProjectCount,
			long fundingProjectCount,
			long restrictedMemberCount) { }
	
	public record PendingProjectItem(
			Long id,
			String title,
			String eventTypeLabel,
			String artistNickname,
			String creatorNickname,
			Long goalAmount,
			LocalDateTime fundingStartAt,
			LocalDateTime fundingEndAt,
			LocalDateTime createdAt) { }
	
	public record ProjectReviewDetail(
			Long id,
			Long artistId,
			String title,
			String description,
			String eventTypeLabel,
			String artistNickname,
			String creatorNickname,
			Long goalAmount,
			LocalDateTime fundingStartAt,
			LocalDateTime fundingEndAt,
			Integer basicBadgeCount,
			Integer specialBadgeCount,
			LocalDateTime identityVerifiedAt,
			String statusLabel,
			LocalDateTime createdAt) {	}
	
	public record AdminProjectItem(
			Long id,
			String title,
			String eventTypeLabel,
			String artistNickname,
			String creatorNickname,
			String statusName,
			String statusLabel,
			Long goalAmount,
			Long fundedAmount,
			Long participantCount,
			int progressPercent,
			LocalDateTime fundingStartAt,
			LocalDateTime fundingEndAt,
			String reviewerNickname,
			LocalDateTime reviewedAt,
			String rejectionReason,
			LocalDateTime createdAt) { }
	
	public record SettlementProjectItem(
			Long projectId,
			String title,
			String artistNickname,
			String creatorNickname,
			Long goalAmount,
			Long fundedAmount,
			Long participantCount,
			int progressPercent,
			LocalDateTime fundingEndAt,
			String bankName,
			String accountLast4,
			String verificationStatusName,
			String verificationStatusLabel) { }
	
	public record CommunityOverviewStats(
			long communityCount,
			long totalMemberCount,
			long activeProjectCount,
			long blockedMemberCount,
			long pendingReportCount) { }
}
