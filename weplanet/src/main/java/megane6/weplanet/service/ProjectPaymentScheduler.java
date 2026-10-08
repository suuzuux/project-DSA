package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.repository.ProjectContributionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 결제 상태 정리 스케줄러 (5분마다 입금 대기 확인, 60분 방치 READY 는 FAILED, 주문별 트랜잭션). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectPaymentScheduler {
	
	private static final long READY_TIMEOUT_MINUTES = 60;
	
	private final ProjectContributionRepository pcr;
	private final ProjectContributionService pcs;
	
	// 이전 실행 5분 후 반복, 서버 시작 1분 후 첫 실행
	@Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
	public void cleanUpPayments() {
		List<String> waitingOrders = pcr.findOrderNosByStatus(FanProjectPaymentStatus.WAITING_FOR_DEPOSIT);
		for (String orderNo : waitingOrders) {
			try {
				pcs.syncWaitingDeposit(orderNo);
			} catch (RuntimeException e) {
				log.warn("[결제 스케줄러] 입금 대기 주문 처리 실패. orderNo={}", orderNo, e);
			}
		}
		
		LocalDateTime readyCutoff = LocalDateTime.now().minusMinutes(READY_TIMEOUT_MINUTES);
		List<String> staleReadyOrders = pcr.findOrderNosByStatusCreatedBefore(
				FanProjectPaymentStatus.READY, readyCutoff);
		for (String orderNo : staleReadyOrders) {
			try {
				pcs.failStaleReadyOrder(orderNo);
			} catch (RuntimeException e) {
				log.warn("[결제 스케줄러] 방치된 주문 정리 실패. orderNo={}", orderNo, e);
			}
		}
		
		if (!waitingOrders.isEmpty() || !staleReadyOrders.isEmpty()) {
			log.info("[결제 스케줄러] 입금대기 {}건 확인, 방치 주문 {}건 정리", waitingOrders.size(), staleReadyOrders.size());
		}
	}
}
