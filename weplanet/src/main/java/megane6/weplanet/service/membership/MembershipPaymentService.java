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
import megane6.weplanet.repository.membership.MembershipOrderRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.membership.MembershipService;
import megane6.weplanet.service.community.CommunityArtistResolver;
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
	// 결제창 주문명/구매자명, 결과 화면 문구를 요청 로케일로 만든다
	private final megane6.weplanet.i18n.Messages messages;
	private final CommunityArtistResolver communityArtistResolver;

	@Transactional
	public ProjectPaymentPrepareResponse prepare(User fan, Long artistId, String idempotencyKey) {
		User artist = requireArtist(artistId);
		validateEligible(fan, artist);
		if (membershipService.isActiveMember(fan, artist)) {
			throw new IllegalStateException("error.membership.alreadyJoined");
		}
		if (!communityJoinService.isJoined(fan, artistId)) {
			throw new IllegalStateException("error.project.joinFirst");
		}
		String key = (idempotencyKey == null || idempotencyKey.isBlank())
				? UUID.randomUUID().toString()
				: idempotencyKey.trim();
		MembershipOrder order = membershipOrderRepository.findByIdempotencyKey(key)
				.map(existing -> reuseReady(existing, fan, artistId))
				.orElseGet(() -> createReady(fan, artist, key));
		String artistName = artist.getNickname() != null ? artist.getNickname() : messages.get("shell.artistFallback");
		String customer = fan.getNickname() != null ? fan.getNickname() : messages.get("community.project.customerFallback");
		return new ProjectPaymentPrepareResponse(
				true,
				tossProperties.clientKey(),
				order.getOrderNo(),
				messages.get("membershipCheckout.orderName", artistName),
				order.getAmount(),
				customer,
				TossVirtualAccountSupport.VALID_HOURS,
				messages.get("community.project.js.openingPayment")
		);
	}

	@Transactional(noRollbackFor = TossPaymentException.class)
	public CommercePaymentResultView confirmVirtualAccount(Long fanId, String paymentKey,
														   String orderId, Long amount) {
		if (paymentKey == null || paymentKey.isBlank() || orderId == null || amount == null) {
			throw new IllegalArgumentException("error.contribution.invalidPayment");
		}
		MembershipOrder order = findMyOrderForUpdate(fanId, orderId);
		if (order.getPaymentStatus() != FanProjectPaymentStatus.READY) {
			if (paymentKey.equals(order.getProviderTransactionId())) {
				return CommercePaymentResultView.fromMembership(order, messages);
			}
			throw new IllegalStateException("shop.error.alreadyProcessedOrder");
		}
		if (!order.getAmount().equals(amount)) {
			throw new IllegalArgumentException("error.contribution.amountMismatch");
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
		return CommercePaymentResultView.fromMembership(order, messages);
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
				.orElseThrow(() -> new IllegalArgumentException("shop.error.orderNotFound"));
		if (!order.getFan().getId().equals(fanId)) {
			throw new AccessDeniedException("error.contribution.ownOrderOnlyView");
		}
		if (order.getPaymentStatus() != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
			return CommercePaymentStatusView.from(order.getPaymentStatus(),
					messages.get(order.getPaymentStatus().getMessageKey()));
		}
		MembershipOrder locked = findMyOrderForUpdate(fanId, orderNo);
		if (locked.getPaymentStatus() == FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
			syncWithToss(locked, LocalDateTime.now());
		}
		return CommercePaymentStatusView.from(locked.getPaymentStatus(),
				messages.get(locked.getPaymentStatus().getMessageKey()));
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
			throw new IllegalStateException("error.contribution.requestKeyUsed");
		}
		return existing;
	}

	private MembershipOrder findMyOrderForUpdate(Long fanId, String orderId) {
		MembershipOrder order = membershipOrderRepository.findByOrderNoForUpdate(orderId)
				.orElseThrow(() -> new IllegalArgumentException("shop.error.orderNotFound"));
		if (!order.getFan().getId().equals(fanId)) {
			throw new AccessDeniedException("error.contribution.ownOrderOnlyPay");
		}
		return order;
	}

	private User requireArtist(Long artistId) {
		return userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("shop.error.artistNotFound"));
	}

	// static 을 뺐다 - 소속 그룹 판정에 주입받은 communityArtistResolver(인스턴스 필드)를 써야 하기 때문
	private void validateEligible(User fan, User artist) {
		if (communityArtistResolver.isArtistOf(fan, artist.getId())) {
			throw new IllegalStateException("error.membership.ownCommunity");
		}
		if (!fan.canParticipateInCommunity()) {
			throw new IllegalStateException("error.community.fanOrArtistOnly");
		}
	}
}
