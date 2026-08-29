package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ProjectContributionRequestDTO;
import megane6.weplanet.domain.dto.ProjectContributionResult;
import megane6.weplanet.domain.dto.ProjectFundingSummary;
import megane6.weplanet.domain.dto.ProjectParticipationView;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.FanProjectCommunityAccessRepository;
import megane6.weplanet.repository.ProjectContributionRepository;
import megane6.weplanet.repository.ProjectRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectContributionService {

    private final ProjectRepository projectRepository;
    private final ProjectContributionRepository contributionRepository;
    private final UserRepository userRepository;
    private final FanProjectCommunityAccessRepository communityAccessRepository;

    @Transactional
    public ProjectContributionResult contribute(
            Long contributorId,
            Long artistId,
            Long projectId,
            ProjectContributionRequestDTO request
    ) {
        User contributor = userRepository.findById(contributorId)
                .orElseThrow(() -> new AccessDeniedException("로그인 회원을 찾을 수 없습니다."));
        if (contributor.getRole() != Role.FAN) {
            throw new AccessDeniedException("팬 회원만 프로젝트에 참여할 수 있습니다.");
        }
        if (!communityAccessRepository.existsByFanIdAndArtistId(contributorId, artistId)) {
            throw new AccessDeniedException("먼저 커뮤니티에 가입해주세요.");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
        validateProject(project, artistId);

        return contributionRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(existing -> existingResult(existing, contributorId, projectId, request.amount()))
                .orElseGet(() -> createContribution(project, contributor, request));
    }
    
    public List<ProjectParticipationView> getMyParticipationHistory(Long contributorId) {
        User contributor = userRepository.findById(contributorId)
                .orElseThrow(() -> new AccessDeniedException("로그인 회원을 찾을 수 없습니다."));
        
        if (contributor.getRole() != Role.FAN) {
            throw new AccessDeniedException("팬 회원만 참여 기록을 확인할 수 있습니다.");
        }
        
        return contributionRepository.findParticipationHistory(contributorId)
                .stream()
                .map(contribution -> new ProjectParticipationView(
                        contribution.getId(),
                        contribution.getProject().getId(),
                        contribution.getProject().getArtist().getId(),
                        contribution.getProject().getTitle(),
                        contribution.getAmount(),
                        contribution.getPaymentStatus().name(),
                        contribution.isAnonymous(),
                        contribution.getPaidAt()
                ))
                .toList();
    }

    private ProjectContributionResult createContribution(
            Project project,
            User contributor,
            ProjectContributionRequestDTO request
    ) {
        LocalDateTime now = LocalDateTime.now();
        String orderNo = createOrderNo(project.getId(), now);

        ProjectContribution contribution = contributionRepository.save(
                ProjectContribution.createPaidMock(
                        project,
                        contributor,
                        orderNo,
                        request.idempotencyKey(),
                        request.amount(),
                        request.depositorName(),
                        request.anonymous(),
                        now
                )
        );

        return toResult(contribution, "모의결제가 완료되어 프로젝트 참여가 기록되었습니다.");
    }

    private ProjectContributionResult existingResult(
            ProjectContribution existing,
            Long contributorId,
            Long projectId,
            Long amount
    ) {
        boolean sameRequest = existing.getContributor().getId().equals(contributorId)
                && existing.getProject().getId().equals(projectId)
                && existing.getAmount().equals(amount);
        if (!sameRequest) {
            throw new IllegalStateException("이미 사용된 결제 요청입니다. 다시 시도해주세요.");
        }
        return toResult(existing, "이미 처리된 참여 요청입니다.");
    }

    private ProjectContributionResult toResult(ProjectContribution contribution, String message) {
        ProjectFundingSummary summary = fundingSummary(contribution.getProject().getId());
        long fundedAmount = summary.fundedAmount();
        long participantCount = summary.participantCount();
        int progressPercent = progressPercent(fundedAmount, contribution.getProject().getGoalAmount());

        return new ProjectContributionResult(
                true,
                contribution.getId(),
                contribution.getOrderNo(),
                contribution.getAmount(),
                fundedAmount,
                participantCount,
                progressPercent,
                message
        );
    }

    private ProjectFundingSummary fundingSummary(Long projectId) {
        return contributionRepository.summarizePaidByProjectIds(
                        List.of(projectId),
                        FanProjectPaymentStatus.PAID
                ).stream()
                .findFirst()
                .orElse(new ProjectFundingSummary(projectId, 0L, 0L));
    }

    private void validateProject(Project project, Long artistId) {
        if (project.getDeletedAt() != null || !project.getArtist().getId().equals(artistId)) {
            throw new IllegalArgumentException("이 커뮤니티의 프로젝트를 찾을 수 없습니다.");
        }
        if (project.getStatus() != FanProjectStatus.APPROVED
                && project.getStatus() != FanProjectStatus.FUNDING) {
            throw new IllegalStateException("승인되어 모금 중인 프로젝트만 참여할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(project.getFundingStartAt())) {
            throw new IllegalStateException("아직 모금이 시작되지 않은 프로젝트입니다.");
        }
        if (now.isAfter(project.getFundingEndAt())) {
            throw new IllegalStateException("모금이 마감된 프로젝트입니다.");
        }
    }

    private String createOrderNo(Long projectId, LocalDateTime now) {
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return "FP-" + projectId + "-" + timestamp + "-" + random;
    }

    private int progressPercent(long fundedAmount, long goalAmount) {
        if (goalAmount <= 0) {
            return 0;
        }
        return (int) Math.min(999, fundedAmount * 100 / goalAmount);
    }
}
