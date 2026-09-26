package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ProjectPaymentStatusView;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.ProjectContributionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가상계좌 안내 화면이 입금 여부를 확인할 때 부르는 주소.
 *
 * 로컬에서는 토스가 localhost 로 웹훅을 보낼 수 없고, 스케줄러는 5분마다 돌아서 입금이 늦게 반영된다.
 * 이 주소는 그 주문만 토스에 바로 조회하므로, 입금 즉시(= 화면 폴링 주기) 확정된다.
 * 본인 주문만 조회할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/payments/toss/orders")
public class ProjectPaymentStatusController {

	private final ProjectContributionService pcs;

	@GetMapping("/{orderNo}/status")
	public ProjectPaymentStatusView status(@PathVariable String orderNo,
										   @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}

		return pcs.refreshDepositStatus(principal.getId(), orderNo);
	}
}
