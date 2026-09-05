package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.ProjectRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.portal.ArtistBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCommunityService {
	private final UserRepository ur;
	private final ProjectRepository pr;
	private final ArtistBlockRepository abr;
	
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
}
