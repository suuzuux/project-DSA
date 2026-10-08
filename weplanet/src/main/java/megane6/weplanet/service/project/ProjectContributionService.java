package megane6.weplanet.service.project;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.config.TossPaymentsProperties;
import megane6.weplanet.domain.dto.ProjectContributionRequestDTO;
import megane6.weplanet.domain.dto.ProjectParticipationView;
import megane6.weplanet.domain.dto.ProjectPaymentPrepareResponse;
import megane6.weplanet.domain.dto.ProjectPaymentResultView;
import megane6.weplanet.domain.dto.ProjectPaymentStatusView;
import megane6.weplanet.domain.dto.payment.TossDepositCallback;
import megane6.weplanet.domain.dto.payment.TossPaymentResponse;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.exception.TossPaymentException;
import megane6.weplanet.repository.project.FanProjectCommunityAccessRepository;
import megane6.weplanet.repository.project.ProjectContributionRepository;
import megane6.weplanet.repository.project.ProjectRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.payment.TossPaymentsClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectContributionService {
    
    // 입금기한은 최대 24시간 (모금 마감이 더 빠르면 마감까지)
    private static final long MAX_DEPOSIT_HOURS = 24;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    
    private final ProjectRepository projectRepository;
    private final ProjectContributionRepository contributionRepository;
    private final UserRepository userRepository;
    private final FanProjectCommunityAccessRepository communityAccessRepository;
    private final ApplicationEventPublisher eventPublisher; // [배지] 입금 확인 시 발행
    private final TossPaymentsProperties tossProperties;
    private final TossPaymentsClient tossClient;
    // 결제창 주문명·구매자명·상태 라벨을 요청 로케일로 만든다.
    private final megane6.weplanet.i18n.Messages messages;
    
    /** 참여하기 - READY 주문을 만들고 결제창 정보를 돌려준다 (같은 멱등 키면 기존 주문 재사용). */
    @Transactional
    public ProjectPaymentPrepareResponse contribute(
            Long contributorId,
            Long artistId,
            Long projectId,
            ProjectContributionRequestDTO request
    ) {
        User contributor = userRepository.findById(contributorId)
                .orElseThrow(() -> new AccessDeniedException("error.project.memberNotFound"));
        if (contributor.getRole() != Role.FAN) {
            throw new AccessDeniedException("error.contribution.fanOnly");
        }
        if (!communityAccessRepository.existsByFanIdAndArtistId(contributorId, artistId)) {
            throw new AccessDeniedException("error.project.joinFirst");
        }
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("error.project.notFound"));
        validateProject(project, artistId);
        
        LocalDateTime now = LocalDateTime.now();
        int validHours = depositValidHours(project, now);
        
        ProjectContribution contribution = contributionRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(existing -> reuseReadyOrder(existing, contributorId, projectId, request.amount()))
                .orElseGet(() -> createReadyOrder(project, contributor, request, now));
        
        return new ProjectPaymentPrepareResponse(
                true,
                tossProperties.clientKey(),
                contribution.getOrderNo(),
                messages.get("community.project.orderName", project.getTitle()),
                contribution.getAmount(),
                contributor.getNickname() != null ? contributor.getNickname() : messages.get("community.project.customerFallback"),
                validHours,
                messages.get("community.project.js.openingPayment")
        );
    }
    
    public List<ProjectParticipationView> getMyParticipationHistory(Long contributorId) {
        User contributor = userRepository.findById(contributorId)
                .orElseThrow(() -> new AccessDeniedException("error.project.memberNotFound"));
        
        if (!contributor.canParticipateInCommunity()) {
            throw new AccessDeniedException("error.contribution.historyFanOrArtistOnly");
        }
        
        return contributionRepository.findParticipationHistory(contributorId)
                .stream()
                // READY·FAILED 주문은 참여 기록에서 숨긴다.
                .filter(contribution -> contribution.getPaymentStatus() != FanProjectPaymentStatus.READY
                        && contribution.getPaymentStatus() != FanProjectPaymentStatus.FAILED)
                .map(ProjectParticipationView::from)
                .toList();
    }
    
    
    /** 결제창 성공 복귀 - 승인 후 가상계좌 정보를 저장한다 (실패 기록이 남도록 noRollbackFor). */
    @Transactional(noRollbackFor = TossPaymentException.class)
    public ProjectPaymentResultView confirmVirtualAccount(
            Long contributorId,
            String paymentKey,
            String orderId,
            Long amount
    ) {
        if (paymentKey == null || paymentKey.isBlank() || orderId == null || amount == null) {
            throw new IllegalArgumentException("error.contribution.invalidPayment");
        }
        
        ProjectContribution contribution = findMyOrderForUpdate(contributorId, orderId);
        
        // 새로고침으로 다시 오면 저장된 결과를 보여준다.
        if (contribution.getPaymentStatus() != FanProjectPaymentStatus.READY) {
            if (paymentKey.equals(contribution.getProviderTransactionId())) {
                return ProjectPaymentResultView.from(contribution);
            }
            throw new IllegalStateException("error.contribution.alreadyProcessedOrder");
        }
        
        // 주소창 amount 는 조작될 수 있어 DB 금액과 비교한다.
        if (!contribution.getAmount().equals(amount)) {
            throw new IllegalArgumentException("error.contribution.amountMismatch");
        }
        
        TossPaymentResponse response;
        try {
            response = tossClient.confirm(paymentKey, orderId, amount);
        } catch (TossPaymentException e) {
            contribution.markFailed();
            throw e;
        }
        
        TossPaymentResponse.VirtualAccount account = response.virtualAccount();
        if (!"WAITING_FOR_DEPOSIT".equals(response.status()) || account == null || account.dueDate() == null) {
            contribution.markFailed();
            throw new TossPaymentException("UNEXPECTED_STATUS", "error.contribution.virtualAccountUnknown");
        }
        
        contribution.markWaitingForDeposit(
                response.paymentKey(),
                account.bankCode(),
                account.accountNumber(),
                toKoreaTime(account.dueDate()),
                response.secret()
        );
        return ProjectPaymentResultView.from(contribution);
    }
    
    /** 결제창 실패 복귀 - READY 인 본인 주문을 FAILED 로 정리한다. */
    @Transactional
    public void failOrder(Long contributorId, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        contributionRepository.findByOrderNoForUpdate(orderId)
                .filter(contribution -> contribution.getContributor().getId().equals(contributorId))
                .filter(contribution -> contribution.getPaymentStatus() == FanProjectPaymentStatus.READY)
                .ifPresent(ProjectContribution::markFailed);
    }
    
    /** 입금 웹훅 처리 - 잘못된 요청은 로그만 남기고 무시한다 (500 이면 토스가 재전송). */
    @Transactional
    public void handleDepositCallback(TossDepositCallback callback) {
        if (callback == null || callback.orderId() == null || callback.secret() == null) {
            log.warn("[토스 웹훅] 필수 값이 없는 요청 무시");
            return;
        }
        
        ProjectContribution contribution =
                contributionRepository.findByOrderNoForUpdate(callback.orderId())
                        .orElse(null);
        if (contribution == null) {
            log.warn("[토스 웹훅] 존재하지 않는 주문 orderId={}", callback.orderId());
            return;
        }
        
        // secret 이 다르면 가짜 웹훅으로 보고 무시한다.
        if (!contribution.matchesDepositSecret(callback.secret())) {
            log.warn("[토스 웹훅] secret 불일치 orderId={}", callback.orderId());
            return;
        }
        
        if (!"DONE".equals(callback.status())) {
            // 입금 완료 외 알림은 기록만 한다.
            log.info("[토스 웹훅] 처리하지 않는 상태 orderId={}, status={}", callback.orderId(), callback.status());
            return;
        }
        
        FanProjectPaymentStatus current = contribution.getPaymentStatus();
        if (current != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT && current != FanProjectPaymentStatus.PAID) {
            log.warn("[토스 웹훅] 입금 완료를 받을 수 없는 상태 orderId={}, current={}", callback.orderId(), current);
            return;
        }
        completePayment(contribution, LocalDateTime.now());
    }
    
    /** [스케줄러] 입금 대기 주문을 토스 실제 상태와 맞춘다 (웹훅 유실·기한 경과 대비). */
    @Transactional
    public void syncWaitingDeposit(String orderNo) {
        ProjectContribution contribution = contributionRepository.findByOrderNoForUpdate(orderNo)
                .orElse(null);
        // 그 사이 웹훅이 처리했을 수 있어 상태를 다시 확인한다.
        if (contribution == null || contribution.getPaymentStatus()
                != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
            return;
        }
        syncWithToss(contribution, LocalDateTime.now());
    }

    /** 입금 확인 화면 폴링 - 입금 대기면 토스에 바로 확인한다. */
    @Transactional
    public ProjectPaymentStatusView refreshDepositStatus(Long contributorId, String orderNo) {
        // 입금 대기가 아니면 잠그지 않고 상태만 읽는다.
        ProjectContribution contribution = contributionRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("shop.error.orderNotFound"));
        if (!contribution.getContributor().getId().equals(contributorId)) {
            throw new AccessDeniedException("error.contribution.ownOrderOnlyView");
        }
        if (contribution.getPaymentStatus() != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
            return ProjectPaymentStatusView.from(contribution,
                    messages.get(contribution.getPaymentStatus().getMessageKey()));
        }

        // 상태를 바꿀 수 있어 행을 잠그고 다시 확인한다.
        ProjectContribution locked = findMyOrderForUpdate(contributorId, orderNo);
        if (locked.getPaymentStatus() == FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
            syncWithToss(locked, LocalDateTime.now());
        }
        return ProjectPaymentStatusView.from(locked,
                messages.get(locked.getPaymentStatus().getMessageKey()));
    }

    /** 입금 대기 주문을 토스 상태와 맞춘다 (스케줄러·폴링 공용). */
    private void syncWithToss(ProjectContribution contribution, LocalDateTime now) {
        String orderNo = contribution.getOrderNo();
        TossPaymentResponse payment;
        try {
            payment = tossClient.getPayment(contribution.getProviderTransactionId());
        } catch (TossPaymentException e) {
            // 조회 실패는 다음 주기에 다시 시도한다.
            log.warn("[결제 동기화] 토스 조회 실패. orderNo={}, code={}", orderNo, e.getCode());
            return;
        }
        
        switch (payment.status()) {
            case "DONE" -> completePayment(contribution, now);
            // 토스에서 취소·만료된 결제
            case "CANCELED", "PARTIAL_CANCELED", "EXPIRED" -> contribution.expire(now);
            default -> {
                // 우리 기준 입금기한이 지났으면 만료
                if (contribution.getDueDate().isBefore(now)) {
                    contribution.expire(now);
                    log.info("[결제 동기화] 입금기한 만료. orderNo={}", orderNo);
                }
            }
        }
    }
    
    /** [스케줄러] 방치된 READY 주문을 FAILED 로 정리한다. */
    @Transactional
    public void failStaleReadyOrder(String orderNo) {
        contributionRepository.findByOrderNoForUpdate(orderNo)
                .filter(contribution -> contribution.getPaymentStatus()
                        == FanProjectPaymentStatus.READY)
                .ifPresent(ProjectContribution::markFailed);
    }
    
    /** 입금 완료 공통 처리 (처음 PAID 가 될 때만 배지 이벤트 발행). */
    private void completePayment(ProjectContribution contribution, LocalDateTime paidAt) {
        if (contribution.markPaid(paidAt)) {
            // [배지] 입금 확인 = 참여 확정 (리스너는 커밋 후 실행)
            eventPublisher.publishEvent(new BadgeActivityEvent(
                    contribution.getContributor().getId(),
                    contribution.getProject().getArtist().getId(),
                    BadgeActivityEvent.Activity.PROJECT_JOINED
            ));
            log.info("[결제] 입금 확인 완료. orderNo={}", contribution.getOrderNo());
        }
    }
    
    private ProjectContribution findMyOrderForUpdate(Long contributorId, String orderId) {
        ProjectContribution contribution = contributionRepository.findByOrderNoForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("shop.error.orderNotFound"));
        if (!contribution.getContributor().getId().equals(contributorId)) {
            throw new AccessDeniedException("error.contribution.ownOrderOnlyPay");
        }
        return contribution;
    }
    
    // 토스 시각 → 한국 시각
    private LocalDateTime toKoreaTime(String isoDateTime) {
        return OffsetDateTime.parse(isoDateTime)
                .atZoneSameInstant(KOREA)
                .toLocalDateTime();
    }
    
    // 새 READY 주문 생성
    private ProjectContribution createReadyOrder(
            Project project,
            User contributor,
            ProjectContributionRequestDTO request,
            LocalDateTime now
    ) {
        String orderNo = createOrderNo(project.getId(), now);
        
        return contributionRepository.save(
                ProjectContribution.createReady(
                        project,
                        contributor,
                        orderNo,
                        request.idempotencyKey(),
                        request.amount(),
                        request.anonymous(),
                        now // 환불 규정 동의 시각
                )
        );
    }
    
    // 같은 요청 키면 같은 사람·프로젝트·금액의 READY 주문만 재사용한다.
    private ProjectContribution reuseReadyOrder(
            ProjectContribution existing,
            Long contributorId,
            Long projectId,
            Long amount
    ) {
        boolean sameRequest = existing.getContributor().getId().equals(contributorId)
                && existing.getProject().getId().equals(projectId)
                && existing.getAmount().equals(amount);
        if (!sameRequest) {
            throw new IllegalStateException("error.contribution.requestKeyUsed");
        }
        if (existing.getPaymentStatus() != FanProjectPaymentStatus.READY) {
            throw new IllegalStateException("error.contribution.alreadyProcessedRequest");
        }
        return existing;
    }
    
    // 입금기한이 모금 마감을 넘지 않게 계산한다.
    private int depositValidHours(Project project, LocalDateTime now) {
        long hoursUntilEnd = Duration.between(now, project.getFundingEndAt()).toHours();
        if (hoursUntilEnd < 1) {
            throw new IllegalStateException("error.contribution.tooCloseToDeadline");
        }
        return (int) Math.min(MAX_DEPOSIT_HOURS, hoursUntilEnd);
    }
    
    private void validateProject(Project project, Long artistId) {
        if (project.getDeletedAt() != null || !project.getArtist().getId().equals(artistId)) {
            throw new IllegalArgumentException("error.contribution.projectNotInCommunity");
        }
        if (project.getStatus() != FanProjectStatus.APPROVED
                && project.getStatus() != FanProjectStatus.FUNDING) {
            throw new IllegalStateException("error.contribution.notFunding");
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(project.getFundingStartAt())) {
            throw new IllegalStateException("error.contribution.notStarted");
        }
        if (now.isAfter(project.getFundingEndAt())) {
            throw new IllegalStateException("error.contribution.closed");
        }
    }
    
    // 토스 orderId 규칙에 맞는 주문번호: FP-{프로젝트ID}-{시각}-{랜덤10자}
    private String createOrderNo(Long projectId, LocalDateTime now) {
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return "FP-" + projectId + "-" + timestamp + "-" + random;
    }
}