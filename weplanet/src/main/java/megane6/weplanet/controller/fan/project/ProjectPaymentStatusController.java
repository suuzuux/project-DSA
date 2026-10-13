package megane6.weplanet.controller.fan.project;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ProjectPaymentStatusView;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.project.ProjectContributionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가상계좌 안내 화면의 입금 확인용 - 본인 주문만 토스에 즉시 조회한다 (로컬은 웹훅을 못 받음). */
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
