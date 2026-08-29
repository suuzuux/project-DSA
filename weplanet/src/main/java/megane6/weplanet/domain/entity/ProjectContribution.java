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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contributor_id", nullable = false)
    private User contributor;

    @Column(name = "order_no", nullable = false, unique = true, length = 50)
    private String orderNo;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "payment_provider", nullable = false, length = 30)
    private String paymentProvider;

    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(nullable = false)
    private Long amount;

    @Convert(converter = PlaintextBytesConverter.class)
    @Column(name = "depositor_name", nullable = false, columnDefinition = "VARBINARY(255)")
    private String depositorName;

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
            String depositorName,
            boolean anonymous,
            LocalDateTime paidAt
    ) {
        this.project = project;
        this.contributor = contributor;
        this.orderNo = orderNo;
        this.idempotencyKey = idempotencyKey;
        this.paymentProvider = "MOCK";
        this.providerTransactionId = "MOCK-" + orderNo;
        this.amount = amount;
        this.depositorName = depositorName;
        this.anonymous = anonymous;
        this.refundPolicyAgreedAt = paidAt;
        this.refundAmount = 0L;
        this.paymentStatus = FanProjectPaymentStatus.PAID;
        this.paidAt = paidAt;
    }

    public static ProjectContribution createPaidMock(
            Project project,
            User contributor,
            String orderNo,
            String idempotencyKey,
            Long amount,
            String depositorName,
            boolean anonymous,
            LocalDateTime paidAt
    ) {
        if (project == null || contributor == null || paidAt == null) {
            throw new IllegalArgumentException("프로젝트와 참여자 정보가 필요합니다.");
        }
        if (amount == null || amount < 1_000 || amount > 3_000_000) {
            throw new IllegalArgumentException("참여 금액은 1,000원 이상 3,000,000원 이하여야 합니다.");
        }
        if (depositorName == null || depositorName.isBlank() || depositorName.trim().length() > 50) {
            throw new IllegalArgumentException("입금자명은 1자 이상 50자 이하로 입력해주세요.");
        }

        return new ProjectContribution(
                project,
                contributor,
                orderNo,
                idempotencyKey,
                amount,
                depositorName.trim(),
                anonymous,
                paidAt
        );
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
