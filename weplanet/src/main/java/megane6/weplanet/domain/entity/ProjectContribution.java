package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.convert.PlaintextBytesConverter;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "fan_project_contribution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectContribution {
    
    public static final long MIN_AMOUNT = 1_000L;
    public static final long MAX_AMOUNT = 3_000_000L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contributor_id", nullable = false)
    private User contributor;
    
    // 토스에 넘기는 orderId 로도 그대로 사용한다.
    @Column(name = "order_no", nullable = false, unique = true, length = 50)
    private String orderNo;
    
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;
    
    @Column(name = "payment_provider", nullable = false, length = 30)
    private String paymentProvider;
    
    // 토스 paymentKey. 결제 승인(confirm) 이후에 채워진다.
    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;
    
    @Column(nullable = false)
    private Long amount;
    
    // ---------- 가상계좌 정보 (READY 상태에서는 전부 null) ----------
    
    // 토스 은행 코드 (예: "20" = 우리은행)
    @Column(name = "virtual_bank_code", length = 3)
    private String virtualBankCode;
    
    // 발급받은 가상계좌 번호. 추후 암호화할 수 있게 VARBINARY + 변환기 사용
    @Convert(converter = PlaintextBytesConverter.class)
    @Column(name = "virtual_account_number", columnDefinition = "VARBINARY(255)")
    private String virtualAccountNumber;
    
    // 입금기한
    @Column(name = "due_date")
    private LocalDateTime dueDate;
    
    // 입금 웹훅이 진짜 토스에서 온 건지 확인하는 값. 화면/DTO로 절대 내보내지 않는다.
    @Column(name = "deposit_secret", length = 100)
    private String depositSecret;
    
    // ---------------------------------------------------------------
    
    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;
    
    @Column(name = "refund_policy_agreed_at", nullable = false)
    private LocalDateTime refundPolicyAgreedAt;
    
    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private FanProjectPaymentStatus paymentStatus;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
    
    @Column(name = "refund_reason", length = 500)
    private String refundReason;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    private ProjectContribution(
            Project project,
            User contributor,
            String orderNo,
            String idempotencyKey,
            Long amount,
            boolean anonymous,
            LocalDateTime refundPolicyAgreedAt
    ) {
        this.project = project;
        this.contributor = contributor;
        this.orderNo = orderNo;
        this.idempotencyKey = idempotencyKey;
        this.paymentProvider = "TOSS";
        this.amount = amount;
        this.anonymous = anonymous;
        this.refundPolicyAgreedAt = refundPolicyAgreedAt;
        this.refundAmount = 0L;
        this.paymentStatus = FanProjectPaymentStatus.READY;
    }
    
    /**
     * [참여하기] 버튼 -> 주문만 먼저 만든다. 아직 돈은 오가지 않은 상태.
     * 금액은 여기서 확정되고, 이후 승인 단계에서 이 금액과 비교해 위변조를 막는다.
     */
    public static ProjectContribution createReady(
            Project project,
            User contributor,
            String orderNo,
            String idempotencyKey,
            Long amount,
            boolean anonymous,
            LocalDateTime refundPolicyAgreedAt
    ) {
        if (project == null || contributor == null || refundPolicyAgreedAt == null) {
            throw new IllegalArgumentException("프로젝트와 참여자 정보가 필요합니다.");
        }
        if (amount == null || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("참여 금액은 1,000원 이상 3,000,000원 이하여야 합니다.");
        }
        
        return new ProjectContribution(
                project,
                contributor,
                orderNo,
                idempotencyKey,
                amount,
                anonymous,
                refundPolicyAgreedAt
        );
    }
    
    /**
     * 토스 결제 승인(confirm) 성공 -> 가상계좌가 발급된 상태로 바꾼다.
     */
    public void markWaitingForDeposit(
            String paymentKey,
            String bankCode,
            String accountNumber,
            LocalDateTime dueDate,
            String secret
    ) {
        if (paymentStatus != FanProjectPaymentStatus.READY) {
            throw new IllegalStateException("결제 대기 중인 주문만 가상계좌를 발급할 수 있습니다.");
        }
        if (paymentKey == null || bankCode == null || accountNumber == null
                || dueDate == null || secret == null) {
            throw new IllegalArgumentException("가상계좌 발급 정보가 올바르지 않습니다.");
        }
        
        this.providerTransactionId = paymentKey;
        this.virtualBankCode = bankCode;
        this.virtualAccountNumber = accountNumber;
        this.dueDate = dueDate;
        this.depositSecret = secret;
        this.paymentStatus = FanProjectPaymentStatus.WAITING_FOR_DEPOSIT;
    }
    
    /**
     * 입금 확인(웹훅) -> 결제 완료.
     * 토스는 같은 웹훅을 여러 번 보낼 수 있어서, 이미 PAID면 에러 없이 그냥 넘어간다.
     *
     * @return 이번 호출로 실제 PAID가 됐으면 true (배지 지급 등 후속 처리는 true일 때만)
     */
    public boolean markPaid(LocalDateTime paidAt) {
        if (paymentStatus == FanProjectPaymentStatus.PAID) {
            return false;
        }
        if (paymentStatus != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
            throw new IllegalStateException("입금 대기 중인 주문만 결제 완료 처리할 수 있습니다.");
        }
        
        this.paymentStatus = FanProjectPaymentStatus.PAID;
        this.paidAt = paidAt;
        return true;
    }
    
    /**
     * 결제창 취소나 승인 API 실패 -> 실패 처리
     */
    public void markFailed() {
        if (paymentStatus != FanProjectPaymentStatus.READY) {
            throw new IllegalStateException("결제 대기 중인 주문만 실패 처리할 수 있습니다.");
        }
        this.paymentStatus = FanProjectPaymentStatus.FAILED;
    }
    
    /**
     * 입금기한이 지나도록 입금이 없으면 만료 처리 (스케줄러에서 호출)
     */
    public void expire(LocalDateTime now) {
        if (paymentStatus != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
            throw new IllegalStateException("입금 대기 중인 주문만 만료 처리할 수 있습니다.");
        }
        this.paymentStatus = FanProjectPaymentStatus.EXPIRED;
        this.cancelledAt = now;
    }
    
    // 웹훅으로 받은 secret이 발급 때 저장한 값과 같은지 확인
    public boolean matchesDepositSecret(String secret) {
        return depositSecret != null && depositSecret.equals(secret);
    }
    
    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}