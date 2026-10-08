package megane6.weplanet.controller.fan.payment;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.exception.TossPaymentException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.project.ProjectContributionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 토스 결제창이 결제 후 돌려보내는 주소 (project.html 의 successUrl / failUrl). */
@Controller
@RequiredArgsConstructor
@RequestMapping("/payments/toss")
public class TossPaymentController {
	
	private final ProjectContributionService pcs;
	private final megane6.weplanet.i18n.Messages messages;
	
	// 가상계좌 발급 요청 완료 -> 승인 처리 후 계좌 안내 화면
	@GetMapping("/success")
	public String success(@RequestParam(required = false) String paymentKey,
						  @RequestParam(required = false) String orderId,
						  @RequestParam(required = false) Long amount,
						  @AuthenticationPrincipal AuthenticatedUser principal,
						  Model model) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		
		try {
			model.addAttribute("result", pcs.confirmVirtualAccount(
					principal.getId(), paymentKey, orderId, amount));
			return "payment/virtual-account";
		} catch (TossPaymentException | IllegalArgumentException
				 | IllegalStateException | AccessDeniedException e) {
			model.addAttribute("message", messages.resolve(e));
			return "payment/fail";
		}
	}
	
	// 결제창에서 취소/실패 -> 주문 정리 후 실패 화면
	@GetMapping("/fail")
	public String fail(@RequestParam(required = false) String code,
					   @RequestParam(required = false) String message,
					   @RequestParam(required = false) String orderId,
					   @AuthenticationPrincipal AuthenticatedUser principal,
					   Model model) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		
		pcs.failOrder(principal.getId(), orderId);
		
		String displayMessage = "PAY_PROCESS_CANCELED".equals(code)
				? messages.get("payment.cancelled")
				: (message != null ? message : messages.get("shop.error.paymentFailedGeneric"));
		
		model.addAttribute("message", displayMessage);
		
		return "payment/fail";
	}
}
