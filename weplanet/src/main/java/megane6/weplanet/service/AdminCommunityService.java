package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ProjectFundingSummary;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.ProjectSettlementAccount;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.SettlementVerificationStatus;
import megane6.weplanet.repository.ProjectContributionRepository;
import megane6.weplanet.repository.ProjectRepository;
import megane6.weplanet.repository.ProjectSettlementAccountRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.portal.ArtistBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCommunityService {
	private final UserRepository ur;
	private final ProjectRepository pr;
	private final ArtistBlockRepository abr;
	private final ProjectContributionRepository pcr;
	private final ProjectSettlementAccountRepository psr;
	
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
	
	@Transactional
	public void verifySettlementAccount(Long projectId, Long adminId) {
		User admin = getAdmin(adminId);
		getSettlementProject(projectId);
		ProjectSettlementAccount account = getSettlementAccount(projectId);
		account.verify(admin);
	}
	
	@Transactional
	public void failSettlementAccountVerification(Long projectId, Long adminId) {
		User admin = getAdmin(adminId);
		getSettlementProject(projectId);
		ProjectSettlementAccount account = getSettlementAccount(projectId);
		account.failVerification(admin);
	}
	
	@Transactional
	public void completeSettlement(Long projectId, Long adminId) {
		User admin = getAdmin(adminId);
		Project project = getSettlementProject(projectId);
		ProjectSettlementAccount account = getSettlementAccount(projectId);
		if (account.getVerificationStatus() != SettlementVerificationStatus.VERIFIED) {
			throw new IllegalStateException("계좌 확인이 완료된 프로젝트만 정산할 수 있습니다.");
		}
		
		project.completeSettlement(admin);
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
}
