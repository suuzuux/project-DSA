package megane6.weplanet.service;

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
import megane6.weplanet.repository.*;
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
				.orElseThrow(() -> new IllegalArgumentException("커뮤니티를 찾을 수 없습니다."));
		AdminCommunityOverviewResponse overview =
				getCommunityOverview(artist.getUsername(), null, "NAME_ASC")
						.stream()
						.filter(item -> item.artistId().equals(artistId))
						.findFirst()
						.orElseThrow(() -> new IllegalArgumentException("커뮤니티 현황을 조회할 수 없습니다."));
		
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
								project.getEventType().getDisplayName(),
								project.getStatus().name(),
								project.getStatus().getDisplayName(),
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
										? "사유 미입력"
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
								"게시글",
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
								"댓글",
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
	
	private String boardTypeLabel(BoardType boardType) {
		return switch (boardType) {
			case FAN -> "팬 게시판";
			case ARTIST -> "아티스트 게시판";
		};
	}
	
	private String reportReasonLabel(ReportReason reason) {
		return switch (reason) {
			case SPAM -> "스팸";
			case ABUSE -> "욕설·혐오";
			case SEXUAL -> "음란물";
			case ETC -> "기타";
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
							project.getEventType().getDisplayName(),
							project.getArtist().getNickname(),
							project.getCreator().getNickname(),
							project.getStatus().name(),
							project.getStatus().getDisplayName(),
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
					account == null ? "-" : account.getBank().getDisplayName(),
					account == null ? "-" : account.getAccountNumberLast4(),
					account == null ? "MISSING" : account.getVerificationStatus().name(),
					verificationLabel(account)
			);
		})
				.toList();
	}
	
	private String verificationLabel(ProjectSettlementAccount account) {
		if (account == null) {
			return "계좌 미등록";
		}
		return switch (account.getVerificationStatus()) {
			case UNVERIFIED -> "확인 대기";
			case VERIFIED -> "확인 완료";
			case FAILED -> "확인 실패";
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
				project.getEventType().getDisplayName(),
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
			case ACTIVE -> "운영 중";
			case DORMANT -> "휴면";
			case SUSPENDED -> "운영 정지";
			case WITHDRAWN -> "탈퇴";
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
			throw new IllegalStateException("계좌 확인이 완료된 프로젝트만 정산할 수 있습니다.");
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
				.orElseThrow(() -> new IllegalStateException("ADMIN만 정산 업무를 처리할 수 있습니다."));
	}
	
	private Project getSettlementProject(Long projectId) {
		Project project = pr.findById(projectId).orElseThrow(() ->
				new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
		if (project.getDeletedAt() != null) {
			throw new IllegalArgumentException("삭제된 프로젝트입니다.");
		}
		if (project.getStatus() != FanProjectStatus.FUNDING_CLOSED) {
			throw new IllegalStateException("모금이 마감된 프로젝트만 정산할 수 있습니다.");
		}
		return project;
	}
	
	private ProjectSettlementAccount getSettlementAccount(Long projectId) {
		return psr.findByProject_Id(projectId).orElseThrow(() ->
				new IllegalArgumentException("등록된 정산 계좌가 없습니다."));
	}
	
	public ProjectReviewDetail getProjectReviewDetail(Long projectId) {
		Project project = pr.findById(projectId).orElseThrow(() ->
				new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
		if (project.getDeletedAt() != null) {
			throw new IllegalArgumentException("삭제된 프로젝트입니다.");
		}
		if (project.getStatus() != FanProjectStatus.PENDING_APPROVAL) {
			throw new IllegalArgumentException("현재 승인 대기 중인 프로젝트가 아닙니다.");
		}
		return new ProjectReviewDetail(
				project.getId(),
				project.getArtist().getId(),
				project.getTitle(),
				project.getDescription(),
				project.getEventType().getDisplayName(),
				project.getArtist().getNickname(),
				project.getCreator().getNickname(),
				project.getGoalAmount(),
				project.getFundingStartAt(),
				project.getFundingEndAt(),
				project.getBasicBadgeCountAtApply(),
				project.getSpecialBadgeCountAtApply(),
				project.getIdentityVerifiedAt(),
				project.getStatus().getDisplayName(),
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
