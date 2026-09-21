package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.repository.ProjectContributionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 팬 프로젝트 결제 상태 정리 스케줄러 (5분마다)
 * 1) 입금 대기 주문 -> 토스에 조회해서 입금됐으면 PAID, 기한 지났으면 EXPIRED
 * 2) 결제창을 닫아 READY로 방치된 주문 -> 60분 지나면 FAILED
 *
 * 주문마다 서비스 메서드를 따로 호출(= 주문마다 별도 트랜잭션)해서 한 건이 실패해도 나머지는 계속 처리되게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectPaymentScheduler {
	
	private static final long READY_TIMEOUT_MINUTES = 60;
	
	private final ProjectContributionRepository pcr;
	private final ProjectContributionService pcs;
	
	// fixedDelay : 이전 실행이 끝나고 5분 뒤 다시 실행 / initialDelay : 서버 시작 1분 뒤 첫 실행
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
