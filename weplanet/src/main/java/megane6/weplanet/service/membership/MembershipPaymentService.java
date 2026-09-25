package megane6.weplanet.service.membership;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.config.TossPaymentsProperties;
import megane6.weplanet.domain.dto.CommercePaymentResultView;
import megane6.weplanet.domain.dto.CommercePaymentStatusView;
import megane6.weplanet.domain.dto.ProjectPaymentPrepareResponse;
import megane6.weplanet.domain.dto.payment.TossPaymentResponse;
import megane6.weplanet.domain.entity.MembershipOrder;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.TossPaymentException;
import megane6.weplanet.repository.MembershipOrderRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.MembershipService;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.payment.TossPaymentsClient;
import megane6.weplanet.service.payment.TossVirtualAccountSupport;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipPaymentService {

	private final MembershipOrderRepository membershipOrderRepository;
	private final UserRepository userRepository;
	private final MembershipService membershipService;
	private final CommunityJoinService communityJoinService;
	private final TossPaymentsProperties tossProperties;
	private final TossPaymentsClient tossClient;

	@Transactional
	public ProjectPaymentPrepareResponse prepare(User fan, Long artistId, String idempotencyKey) {
		User artist = requireArtist(artistId);
		validateEligible(fan, artist);
		if (membershipService.isActiveMember(fan, artist)) {
			throw new IllegalStateException("이미 멤버십에 가입되어 있습니다.");
		}
		if (!communityJoinService.isJoined(fan, artistId)) {
			throw new IllegalStateException("먼저 커뮤니티에 가입해주세요.");
		}
		String key = (idempotencyKey == null || idempotencyKey.isBlank())
				? UUID.randomUUID().toString()
				: idempotencyKey.trim();
		MembershipOrder order = membershipOrderRepository.findByIdempotencyKey(key)
				.map(existing -> reuseReady(existing, fan, artistId))
				.orElseGet(() -> createReady(fan, artist, key));
		String artistName = artist.getNickname() != null ? artist.getNickname() : "아티스트";
		String customer = fan.getNickname() != null ? fan.getNickname() : "WePlaNet 회원";
		return new ProjectPaymentPrepareResponse(
				true,
				tossProperties.clientKey(),
				order.getOrderNo(),
				artistName + " 멤버십 (1년)",
				order.getAmount(),
				customer,
				TossVirtualAccountSupport.VALID_HOURS,
				"결제창을 여는 중입니다."
		);
	}

	@Transactional(noRollbackFor = TossPaymentException.class)
	public CommercePaymentResultView confirmVirtualAccount(Long fanId, String paymentKey,
														   String orderId, Long amount) {
		if (paymentKey == null || paymentKey.isBlank() || orderId == null || amount == null) {
			throw new IllegalArgumentException("결제 정보가 올바르지 않습니다.");
		}
		MembershipOrder order = findMyOrderForUpdate(fanId, orderId);
		if (order.getPaymentStatus() != FanProjectPaymentStatus.READY) {
			if (paymentKey.equals(order.getProviderTransactionId())) {
				return CommercePaymentResultView.fromMembership(order);
			}
			throw new IllegalStateException("이미 처리된 주문입니다.");
		}
		if (!order.getAmount().equals(amount)) {
			throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다.");
		}
		TossPaymentResponse response;
		try {
			response = tossClient.confirm(paymentKey, orderId, amount);
		} catch (TossPaymentException e) {
			order.markFailed();
			throw e;
		}
		try {
			TossVirtualAccountSupport.requireWaitingVirtualAccount(response);
		} catch (TossPaymentException e) {
			order.markFailed();
			throw e;
		}
		TossPaymentResponse.VirtualAccount account = response.virtualAccount();
		order.markWaitingForDeposit(
				response.paymentKey(),
				account.bankCode(),
				account.accountNumber(),
				TossVirtualAccountSupport.toKoreaTime(account.dueDate()),
				response.secret()
		);
		return CommercePaymentResultView.fromMembership(order);
	}

	@Transactional
	public void failOrder(Long fanId, String orderId) {
		if (orderId == null || orderId.isBlank()) {
			return;
		}
		membershipOrderRepository.findByOrderNoForUpdate(orderId)
				.filter(order -> order.getFan().getId().equals(fanId))
				.filter(order -> order.getPaymentStatus() == FanProjectPaymentStatus.READY)
				.ifPresent(MembershipOrder::markFailed);
	}

	@Transactional
	public void syncWaitingDeposit(String orderNo) {
		MembershipOrder order = membershipOrderRepository.findByOrderNoForUpdate(orderNo).orElse(null);
		if (order == null || order.getPaymentStatus() != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
			return;
		}
		syncWithToss(order, LocalDateTime.now());
	}

	@Transactional
	public CommercePaymentStatusView refreshDepositStatus(Long fanId, String orderNo) {
		MembershipOrder order = membershipOrderRepository.findByOrderNo(orderNo)
				.orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
		if (!order.getFan().getId().equals(fanId)) {
			throw new AccessDeniedException("본인 주문만 확인할 수 있습니다.");
		}
		if (order.getPaymentStatus() != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
			return CommercePaymentStatusView.from(order.getPaymentStatus());
		}
		MembershipOrder locked = findMyOrderForUpdate(fanId, orderNo);
		if (locked.getPaymentStatus() == FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
			syncWithToss(locked, LocalDateTime.now());
		}
		return CommercePaymentStatusView.from(locked.getPaymentStatus());
	}

	@Transactional
	public void failStaleReadyOrder(String orderNo) {
		membershipOrderRepository.findByOrderNoForUpdate(orderNo)
				.filter(order -> order.getPaymentStatus() == FanProjectPaymentStatus.READY)
				.ifPresent(MembershipOrder::markFailed);
	}

	private void syncWithToss(MembershipOrder order, LocalDateTime now) {
		TossPaymentResponse payment;
		try {
			payment = tossClient.getPayment(order.getProviderTransactionId());
		} catch (TossPaymentException e) {
			log.warn("[멤버십 결제] 토스 조회 실패. orderNo={}, code={}", order.getOrderNo(), e.getCode());
			return;
		}
		switch (payment.status()) {
			case "DONE" -> completePayment(order, now);
			case "CANCELED", "PARTIAL_CANCELED", "EXPIRED" -> order.expire(now);
			default -> {
				if (order.getDueDate() != null && order.getDueDate().isBefore(now)) {
					order.expire(now);
				}
			}
		}
	}

	private void completePayment(MembershipOrder order, LocalDateTime paidAt) {
		if (!order.markPaid(paidAt)) {
			return;
		}
		membershipService.join(order.getFan(), order.getArtist());
		log.info("[멤버십 결제] 입금 확인 완료. orderNo={}", order.getOrderNo());
	}

	private MembershipOrder createReady(User fan, User artist, String key) {
		return membershipOrderRepository.save(MembershipOrder.createReady(
				fan,
				artist,
				TossVirtualAccountSupport.newOrderNo("MB", artist.getId(), LocalDateTime.now()),
				key
		));
	}

	private MembershipOrder reuseReady(MembershipOrder existing, User fan, Long artistId) {
		boolean same = existing.getFan().getId().equals(fan.getId())
				&& existing.getArtist().getId().equals(artistId)
				&& existing.getPaymentStatus() == FanProjectPaymentStatus.READY
				&& existing.getAmount().equals(MembershipOrder.YEARLY_PRICE);
		if (!same) {
			throw new IllegalStateException("이미 사용된 결제 요청입니다. 다시 시도해주세요.");
		}
		return existing;
	}

	private MembershipOrder findMyOrderForUpdate(Long fanId, String orderId) {
		MembershipOrder order = membershipOrderRepository.findByOrderNoForUpdate(orderId)
				.orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
		if (!order.getFan().getId().equals(fanId)) {
			throw new AccessDeniedException("본인 주문만 결제할 수 있습니다.");
		}
		return order;
	}

	private User requireArtist(Long artistId) {
		return userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
	}

	private static void validateEligible(User fan, User artist) {
		if (fan.getId().equals(artist.getId())) {
			throw new IllegalStateException("본인 커뮤니티 멤버십에는 가입할 수 없습니다.");
		}
		if (fan.getRole() != Role.FAN && fan.getRole() != Role.ARTIST) {
			throw new IllegalStateException("팬 또는 아티스트 계정만 이용할 수 있는 기능입니다.");
		}
	}
}
