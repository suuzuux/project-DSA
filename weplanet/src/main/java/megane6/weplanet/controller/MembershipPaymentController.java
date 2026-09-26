package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.CommercePaymentStatusView;
import megane6.weplanet.domain.dto.ProjectPaymentPrepareResponse;
import megane6.weplanet.domain.entity.MembershipOrder;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.exception.TossPaymentException;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.membership.MembershipPaymentService;
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
public class MembershipPaymentController {

	private final MembershipPaymentService membershipPaymentService;
	private final AuthenticatedUserResolver userResolver;
	private final UserRepository userRepository;

	@GetMapping("/payments/membership/{artistId}/checkout")
	public String checkout(@PathVariable Long artistId,
						   @AuthenticationPrincipal AuthenticatedUser principal,
						   Model model) {
		if (principal == null) {
			return "redirect:/login";
		}
		User artist = userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
		model.addAttribute("artistId", artistId);
		model.addAttribute("artistName", artist.getNickname());
		model.addAttribute("amount", MembershipOrder.YEARLY_PRICE);
		return "payment/membership-checkout";
	}

	@PostMapping("/payments/membership/{artistId}/prepare")
	@ResponseBody
	public ProjectPaymentPrepareResponse prepare(@PathVariable Long artistId,
												 @RequestParam(required = false) String idempotencyKey,
												 @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return membershipPaymentService.prepare(
				userResolver.requireAuthenticated(principal), artistId, idempotencyKey);
	}

	@GetMapping("/payments/membership/success")
	public String success(@RequestParam(required = false) String paymentKey,
						  @RequestParam(required = false) String orderId,
						  @RequestParam(required = false) Long amount,
						  @AuthenticationPrincipal AuthenticatedUser principal,
						  Model model) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		try {
			model.addAttribute("result", membershipPaymentService.confirmVirtualAccount(
					principal.getId(), paymentKey, orderId, amount));
			model.addAttribute("paidMessage", "멤버십 가입이 확정되었습니다.");
			model.addAttribute("waitingMessage", "아래 계좌로 입금기한 안에 입금하면 멤버십이 활성화돼요.");
			model.addAttribute("statusUrl", "/payments/membership/orders/" + orderId + "/status");
			return "payment/commerce-virtual-account";
		} catch (TossPaymentException | IllegalArgumentException
				 | IllegalStateException | AccessDeniedException e) {
			model.addAttribute("message", e.getMessage());
			return "payment/fail";
		}
	}

	@GetMapping("/payments/membership/fail")
	public String fail(@RequestParam(required = false) String code,
					   @RequestParam(required = false) String message,
					   @RequestParam(required = false) String orderId,
					   @AuthenticationPrincipal AuthenticatedUser principal,
					   Model model) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		membershipPaymentService.failOrder(principal.getId(), orderId);
		model.addAttribute("message", "PAY_PROCESS_CANCELED".equals(code)
				? "결제를 취소했어요."
				: (message != null ? message : "결제에 실패했습니다."));
		return "payment/fail";
	}

	@GetMapping("/payments/membership/orders/{orderNo}/status")
	@ResponseBody
	public CommercePaymentStatusView status(@PathVariable String orderNo,
											@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return membershipPaymentService.refreshDepositStatus(principal.getId(), orderNo);
	}
}
