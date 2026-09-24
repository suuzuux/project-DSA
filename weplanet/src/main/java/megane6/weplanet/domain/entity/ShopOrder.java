package megane6.weplanet.domain.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.convert.PlaintextBytesConverter;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.ShopOrderSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shop_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "buyer_id", nullable = false)
	private User buyer;

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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ShopOrderSource source;

	@Column(name = "virtual_bank_code", length = 3)
	private String virtualBankCode;

	@Convert(converter = PlaintextBytesConverter.class)
	@Column(name = "virtual_account_number", columnDefinition = "VARBINARY(255)")
	private String virtualAccountNumber;

	@Column(name = "due_date")
	private LocalDateTime dueDate;

	@Column(name = "deposit_secret", length = 100)
	private String depositSecret;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false, length = 30)
	private FanProjectPaymentStatus paymentStatus;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ShopOrderItem> items = new ArrayList<>();

	private ShopOrder(User buyer, String orderNo, String idempotencyKey, Long amount, ShopOrderSource source) {
		this.buyer = buyer;
		this.orderNo = orderNo;
		this.idempotencyKey = idempotencyKey;
		this.paymentProvider = "TOSS";
		this.amount = amount;
		this.source = source;
		this.paymentStatus = FanProjectPaymentStatus.READY;
	}

	public static ShopOrder createReady(User buyer, String orderNo, String idempotencyKey,
										Long amount, ShopOrderSource source) {
		if (buyer == null || orderNo == null || idempotencyKey == null || source == null) {
			throw new IllegalArgumentException("주문 정보가 올바르지 않습니다.");
		}
		if (amount == null || amount < 1) {
			throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
		}
		return new ShopOrder(buyer, orderNo, idempotencyKey, amount, source);
	}

	public void addItem(ShopOrderItem item) {
		this.items.add(item);
	}

	public void markWaitingForDeposit(String paymentKey, String bankCode, String accountNumber,
									  LocalDateTime dueDate, String secret) {
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

	public void markFailed() {
		if (paymentStatus != FanProjectPaymentStatus.READY) {
			throw new IllegalStateException("결제 대기 중인 주문만 실패 처리할 수 있습니다.");
		}
		this.paymentStatus = FanProjectPaymentStatus.FAILED;
	}

	public void expire(LocalDateTime now) {
		if (paymentStatus != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
			throw new IllegalStateException("입금 대기 중인 주문만 만료 처리할 수 있습니다.");
		}
		this.paymentStatus = FanProjectPaymentStatus.EXPIRED;
		this.cancelledAt = now;
	}

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
