package megane6.weplanet.service.membership;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.repository.MembershipOrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipPaymentScheduler {

	private static final long READY_TIMEOUT_MINUTES = 60;

	private final MembershipOrderRepository membershipOrderRepository;
	private final MembershipPaymentService membershipPaymentService;

	@Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 120 * 1000L)
	public void cleanUpPayments() {
		List<String> waiting = membershipOrderRepository.findOrderNosByStatus(
				FanProjectPaymentStatus.WAITING_FOR_DEPOSIT);
		for (String orderNo : waiting) {
			try {
				membershipPaymentService.syncWaitingDeposit(orderNo);
			} catch (RuntimeException e) {
				log.warn("[멤버십 결제 스케줄러] 입금 대기 처리 실패. orderNo={}", orderNo, e);
			}
		}
		LocalDateTime readyCutoff = LocalDateTime.now().minusMinutes(READY_TIMEOUT_MINUTES);
		List<String> stale = membershipOrderRepository.findOrderNosByStatusCreatedBefore(
				FanProjectPaymentStatus.READY, readyCutoff);
		for (String orderNo : stale) {
			try {
				membershipPaymentService.failStaleReadyOrder(orderNo);
			} catch (RuntimeException e) {
				log.warn("[멤버십 결제 스케줄러] 방치 주문 정리 실패. orderNo={}", orderNo, e);
			}
		}
	}
}
