package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.CommercePaymentStatusView;
import megane6.weplanet.domain.dto.ProjectPaymentPrepareResponse;
import megane6.weplanet.domain.dto.ShopShippingRequest;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.exception.TossPaymentException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.shop.ShopPaymentService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
	private final MessageSource messageSource;
	private final megane6.weplanet.i18n.Messages messages;

	// SETTINGS-03: 화면 언어에 맞춰 메시지를 가져오는 헬퍼 (SettingsController.msg()와 동일한 패턴)
	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	@PostMapping("/shop/payments/prepare-cart")
	@ResponseBody
	public ProjectPaymentPrepareResponse prepareCart(@RequestParam(required = false) String idempotencyKey,
													 @RequestParam(required = false) String receiverName,
													 @RequestParam(required = false) String receiverPhone,
													 @RequestParam(required = false) String zipcode,
													 @RequestParam(required = false) String address1,
													 @RequestParam(required = false) String address2,
													 @RequestParam(required = false) String deliveryMemo,
													 @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return shopPaymentService.prepareCart(
				userResolver.requireAuthenticated(principal),
				idempotencyKey,
				ShopShippingRequest.of(receiverName, receiverPhone, zipcode, address1, address2, deliveryMemo));
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
			model.addAttribute("paidMessage", msg("shop.msg.orderConfirmed"));
			model.addAttribute("waitingMessage", msg("shop.msg.waitingDeposit"));
			model.addAttribute("statusUrl", "/payments/shop/orders/" + orderId + "/status");
			return "payment/commerce-virtual-account";
		} catch (TossPaymentException | IllegalArgumentException
				 | IllegalStateException | AccessDeniedException e) {
			model.addAttribute("message", messages.resolve(e));
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
				? msg("shop.error.paymentCancelled")
				: (message != null ? message : msg("shop.error.paymentFailedGeneric")));
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
