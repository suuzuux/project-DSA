package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.CommercePaymentStatusView;
import megane6.weplanet.domain.dto.ProjectPaymentPrepareResponse;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.exception.TossPaymentException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.shop.ShopPaymentService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class ShopPaymentController {

	private final ShopPaymentService shopPaymentService;
	private final AuthenticatedUserResolver userResolver;

	@PostMapping("/shop/payments/prepare-cart")
	@ResponseBody
	public ProjectPaymentPrepareResponse prepareCart(@RequestParam(required = false) String idempotencyKey,
													 @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return shopPaymentService.prepareCart(userResolver.requireAuthenticated(principal), idempotencyKey);
	}

	@PostMapping("/shop/payments/prepare-buy")
	@ResponseBody
	public ProjectPaymentPrepareResponse prepareBuy(@RequestParam String productId,
													@RequestParam(defaultValue = "1") int quantity,
													@RequestParam(required = false) String idempotencyKey,
													@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return shopPaymentService.prepareBuyNow(
				userResolver.requireAuthenticated(principal), productId, quantity, idempotencyKey);
	}

	@GetMapping("/payments/shop/success")
	public String success(@RequestParam(required = false) String paymentKey,
						  @RequestParam(required = false) String orderId,
						  @RequestParam(required = false) Long amount,
						  @AuthenticationPrincipal AuthenticatedUser principal,
						  Model model) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		try {
			model.addAttribute("result", shopPaymentService.confirmVirtualAccount(
					principal.getId(), paymentKey, orderId, amount));
			model.addAttribute("paidMessage", "주문이 확정되었습니다.");
			model.addAttribute("waitingMessage", "아래 계좌로 입금기한 안에 입금하면 주문이 확정돼요.");
			model.addAttribute("statusUrl", "/payments/shop/orders/" + orderId + "/status");
			return "payment/commerce-virtual-account";
		} catch (TossPaymentException | IllegalArgumentException
				 | IllegalStateException | AccessDeniedException e) {
			model.addAttribute("message", e.getMessage());
			return "payment/fail";
		}
	}

	@GetMapping("/payments/shop/fail")
	public String fail(@RequestParam(required = false) String code,
					   @RequestParam(required = false) String message,
					   @RequestParam(required = false) String orderId,
					   @AuthenticationPrincipal AuthenticatedUser principal,
					   Model model) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		shopPaymentService.failOrder(principal.getId(), orderId);
		model.addAttribute("message", "PAY_PROCESS_CANCELED".equals(code)
				? "결제를 취소했어요."
				: (message != null ? message : "결제에 실패했습니다."));
		return "payment/fail";
	}

	@GetMapping("/payments/shop/orders/{orderNo}/status")
	@ResponseBody
	public CommercePaymentStatusView status(@PathVariable String orderNo,
											@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return shopPaymentService.refreshDepositStatus(principal.getId(), orderNo);
	}
}
