package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.payment.TossDepositCallback;
import megane6.weplanet.service.ProjectContributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments/toss")
public class TossWebhookController {
	
	private final ProjectContributionService pcs;
	
	@PostMapping("/webhook")
	public ResponseEntity<Void> depositCallback(@RequestBody TossDepositCallback callback) {
		pcs.handleDepositCallback(callback);
		// 10초 안에 200을 받아야 토스가 재전송하지 않는다.
		return ResponseEntity.ok().build();
	}
}
