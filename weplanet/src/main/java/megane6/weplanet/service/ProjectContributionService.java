package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.config.TossPaymentsProperties;
import megane6.weplanet.domain.dto.ProjectContributionRequestDTO;
import megane6.weplanet.domain.dto.ProjectParticipationView;
import megane6.weplanet.domain.dto.ProjectPaymentPrepareResponse;
import megane6.weplanet.domain.dto.ProjectPaymentResultView;
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
import megane6.weplanet.repository.FanProjectCommunityAccessRepository;
import megane6.weplanet.repository.ProjectContributionRepository;
import megane6.weplanet.repository.ProjectRepository;
import megane6.weplanet.repository.UserRepository;
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
    
    // 가상계좌 입금기한은 최대 24시간. 모금 마감이 더 빠르면 마감 시각까지만.
    private static final long MAX_DEPOSIT_HOURS = 24;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    
    private final ProjectRepository projectRepository;
    private final ProjectContributionRepository contributionRepository;
    private final UserRepository userRepository;
    private final FanProjectCommunityAccessRepository communityAccessRepository;
    private final ApplicationEventPublisher eventPublisher; // [배지] 입금 확인 시 발행 (Step 5에서 사용)
    private final TossPaymentsProperties tossProperties;
    private final TossPaymentsClient tossClient;
    
    /**
     * [참여하기] - 주문(READY)을 만들고, 토스 결제창에 필요한 정보를 돌려준다.
     * 같은 idempotencyKey로 다시 요청하면(더블클릭, 재시도) 새로 만들지 않고 기존 주문을 재사용한다.
     */
    @Transactional
    public ProjectPaymentPrepareResponse contribute(
            Long contributorId,
            Long artistId,
            Long projectId,
            ProjectContributionRequestDTO request
    ) {
        User contributor = userRepository.findById(contributorId)
                .orElseThrow(() -> new AccessDeniedException("로그인 회원을 찾을 수 없습니다."));
        if (contributor.getRole() != Role.FAN) {
            throw new AccessDeniedException("팬 계정만 프로젝트에 참여할 수 있습니다.");
        }
        if (!communityAccessRepository.existsByFanIdAndArtistId(contributorId, artistId)) {
            throw new AccessDeniedException("먼저 커뮤니티에 가입해주세요.");
        }
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
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
                project.getTitle() + " 참여",
                contribution.getAmount(),
                contributor.getNickname() != null ? contributor.getNickname() : "WePlaNet 회원",
                validHours,
                "결제창을 여는 중입니다."
        );
    }
    
    public List<ProjectParticipationView> getMyParticipationHistory(Long contributorId) {
        User contributor = userRepository.findById(contributorId)
                .orElseThrow(() -> new AccessDeniedException("로그인 회원을 찾을 수 없습니다."));
        
        if (contributor.getRole() != Role.FAN && contributor.getRole() != Role.ARTIST) {
            throw new AccessDeniedException("팬 또는 아티스트 계정만 참여 기록을 확인할 수 있습니다.");
        }
        
        return contributionRepository.findParticipationHistory(contributorId)
                .stream()
                // 결제창을 열기만 했거나(READY) 닫아서 실패한(FAILED) 주문은 참여 기록이 아니므로 숨긴다.
                .filter(contribution -> contribution.getPaymentStatus() != FanProjectPaymentStatus.READY
                        && contribution.getPaymentStatus() != FanProjectPaymentStatus.FAILED)
                .map(ProjectParticipationView::from)
                .toList();
    }
    
    
    /**
     * 토스 결제창 -> successUrl 로 돌아왔을 때: 결제 승인 요청 후 가상계좌 발급 정보를 저장한다.
     *
     * noRollbackFor: 토스 승인이 실패하면 주문을 FAILED 로 바꾼 뒤 예외를 던지는데,
     * 롤백되면 FAILED 기록까지 사라지므로 이 예외에는 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = TossPaymentException.class)
    public ProjectPaymentResultView confirmVirtualAccount(
            Long contributorId,
            String paymentKey,
            String orderId,
            Long amount
    ) {
        if (paymentKey == null || paymentKey.isBlank() || orderId == null || amount == null) {
            throw new IllegalArgumentException("결제 정보가 올바르지 않습니다.");
        }
        
        ProjectContribution contribution = findMyOrderForUpdate(contributorId, orderId);
        
        // 새로고침 등으로 다시 들어온 경우: 토스에 다시 요청하지 않고 저장된 결과를 보여준다.
        if (contribution.getPaymentStatus() != FanProjectPaymentStatus.READY) {
            if (paymentKey.equals(contribution.getProviderTransactionId())) {
                return ProjectPaymentResultView.from(contribution);
            }
            throw new IllegalStateException("이미 처리된 주문입니다. 참여 내역을 확인해주세요.");
        }
        
        // 주소창의 amount 는 사용자가 바꿀 수 있으므로, 반드시 DB 금액과 비교한다.
        if (!contribution.getAmount().equals(amount)) {
            throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다.");
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
            throw new TossPaymentException("UNEXPECTED_STATUS", "가상계좌 발급 결과를 확인할 수 없습니다. 다시 시도해주세요.");
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
    
    /**
     * 토스 결제창 -> failUrl 로 돌아왔을 때: 아직 READY 인 본인 주문이면 FAILED 로 정리한다.
     */
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
    
    /**
     * 토스 입금 웹훅 처리
     * 잘못된 요청(모르는 주문, secret 불일치)은 예외를 던지지 않고 로그만 남기고 무시한다
     * -> 예외로 500을 돌려주면 토스가 같은 요청을 계속 재전송하기 때문
     */
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
        
        // 가짜 웹훅 차단 : 승인 때 저장한 secret 과 달라면(?) 무시
        if (!contribution.matchesDepositSecret(callback.secret())) {
            log.warn("[토스 웹훅] secret 불일치 orderId={}", callback.orderId());
            return;
        }
        
        if (!"DONE".equals(callback.status())) {
            // 입금 완료가 아닌 알림(취소 등)은 이번 단계에서는 기록만 한다. (취소·환불은 다음 작업)
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
    
    /**
     * [스케줄러] 입금 대기 주문 하나를 토스의 실제 상태와 맞춘다.
     * 웹훅이 유실되거나(로컬 개발 환경 포함) 입금기한이 지난 경우를 정리한다.
     */
    @Transactional
    public void syncWaitingDeposit(String orderNo) {
        ProjectContribution contribution = contributionRepository.findByOrderNoForUpdate(orderNo)
                .orElse(null);
        // 조회하는 사이에 웹훅이 먼저 처리했을 수 있으므로 상태를 다시 확인
        if (contribution == null || contribution.getPaymentStatus()
                != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        TossPaymentResponse payment;
        try {
            payment = tossClient.getPayment(contribution.getProviderTransactionId());
        } catch (TossPaymentException e) {
            // 조회 실패는 다음 주기에 다시 시도하면 되므로 기록만 남긴다
            log.warn("[결제 동기화] 토스 조회 실패. orderNo={}, code={}", orderNo, e.getCode());
            return;
        }
        
        switch (payment.status()) {
            case "DONE" -> completePayment(contribution, now);
            // 토스 쪽에서 취소/만료된 결제. 취소·환불 상태는 다음 작업에서 따로 나눌 예정
            case "CANCELED", "PARTIAL_CANCELED", "EXPIRED" -> contribution.expire(now);
            default -> {
                // 아직 입금 대기인데 우리 기준 입금기한이 지났으면 만료
                if (contribution.getDueDate().isBefore(now)) {
                    contribution.expire(now);
                    log.info("[결제 동기화] 입금기한 만료. orderNo={}", orderNo);
                }
            }
        }
    }
    
    /**
     * [스케줄러] 결제창을 닫아버려서 READY 로 남은 오래된 주문을 FAILED로 정리한다.
     */
    @Transactional
    public void failStaleReadyOrder(String orderNo) {
        contributionRepository.findByOrderNoForUpdate(orderNo)
                .filter(contribution -> contribution.getPaymentStatus()
                        == FanProjectPaymentStatus.READY)
                .ifPresent(ProjectContribution::markFailed);
    }
    
    /**
     * 입금 완료 공통 처리 (웹훅, 스케줄러 둘 다 여기로 온다)
     * markPaid가 true일 때(이번에 처음 PAID)만 배지 이벤트를 발행해서 중복 지급을 막는다.
     */
    private void completePayment(ProjectContribution contribution, LocalDateTime paidAt) {
        if (contribution.markPaid(paidAt)) {
            // [배지] 입금 확인 = 실제 참여 확정. 배지 리스너는 커밋 후(AFTER_COMMIT)에 실행됨
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
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        if (!contribution.getContributor().getId().equals(contributorId)) {
            throw new AccessDeniedException("본인 주문만 결제할 수 있습니다.");
        }
        return contribution;
    }
    
    // 토스 시각("2026-09-22T17:30:00+09:00")을 한국 시각 LocalDateTime 으로 변환
    private LocalDateTime toKoreaTime(String isoDateTime) {
        return OffsetDateTime.parse(isoDateTime)
                .atZoneSameInstant(KOREA)
                .toLocalDateTime();
    }
    
    // 새 주문(READY) 생성. 실제 결제는 토스 결제창 -> 승인 -> 입금 순서로 진행된다.
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
                        now // 환불 규정 동의 시각 = 참여하기 누른 시각
                )
        );
    }
    
    // 같은 요청 키로 다시 들어온 경우: 같은 사람·같은 프로젝트·같은 금액이고 아직 READY일 때만 재사용
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
            throw new IllegalStateException("이미 사용된 결제 요청입니다. 다시 시도해주세요.");
        }
        if (existing.getPaymentStatus() != FanProjectPaymentStatus.READY) {
            throw new IllegalStateException("이미 처리된 참여 요청입니다. 참여 내역을 확인해주세요.");
        }
        return existing;
    }
    
    // 입금기한(시간)이 모금 마감을 넘지 않게 계산한다.
    private int depositValidHours(Project project, LocalDateTime now) {
        long hoursUntilEnd = Duration.between(now, project.getFundingEndAt()).toHours();
        if (hoursUntilEnd < 1) {
            throw new IllegalStateException("모금 마감까지 1시간이 남지 않아 가상계좌를 발급할 수 없습니다.");
        }
        return (int) Math.min(MAX_DEPOSIT_HOURS, hoursUntilEnd);
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
    
    // 토스 orderId 규칙(6~64자, 영문·숫자·-·_)에 맞는 형식: FP-{프로젝트ID}-{시각}-{랜덤10자}
    private String createOrderNo(Long projectId, LocalDateTime now) {
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return "FP-" + projectId + "-" + timestamp + "-" + random;
    }
}