package megane6.weplanet.service.shop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.config.TossPaymentsProperties;
import megane6.weplanet.domain.dto.CommercePaymentResultView;
import megane6.weplanet.domain.dto.CommercePaymentStatusView;
import megane6.weplanet.domain.dto.ProjectPaymentPrepareResponse;
import megane6.weplanet.domain.dto.ShopCartSummaryView;
import megane6.weplanet.domain.dto.ShopProductView;
import megane6.weplanet.domain.dto.payment.TossPaymentResponse;
import megane6.weplanet.domain.entity.ShopCartItem;
import megane6.weplanet.domain.entity.ShopOrder;
import megane6.weplanet.domain.entity.ShopOrderItem;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.ShopOrderSource;
import megane6.weplanet.exception.TossPaymentException;
import megane6.weplanet.repository.ShopCartItemRepository;
import megane6.weplanet.repository.ShopOrderRepository;
import megane6.weplanet.service.ShopCartService;
import megane6.weplanet.service.ShopService;
import megane6.weplanet.service.payment.TossPaymentsClient;
import megane6.weplanet.service.payment.TossVirtualAccountSupport;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopPaymentService {

	private final ShopOrderRepository shopOrderRepository;
	private final ShopCartItemRepository shopCartItemRepository;
	private final ShopCartService shopCartService;
	private final ShopService shopService;
	private final GoodsService goodsService;
	private final TossPaymentsProperties tossProperties;
	private final TossPaymentsClient tossClient;

	@Transactional
	public ProjectPaymentPrepareResponse prepareCart(User buyer, String idempotencyKey) {
		List<ShopCartItem> rows = shopCartItemRepository.findByUserOrderByCreatedAtAsc(buyer);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("장바구니가 비어 있습니다.");
		}
		ShopCartSummaryView cart = shopCartService.getCartSummary(buyer);
		for (ShopCartItem row : rows) {
			ShopProductView product = shopService.findProduct(row.getProductId())
					.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
			shopService.requirePurchasable(buyer, product);
			if (product.soldOut()) {
				throw new IllegalArgumentException("품절된 상품이 포함되어 있습니다.");
			}
			goodsService.ensureVariantStock(ShopCheckoutService.parseVariantId(row.getProductId()), row.getQuantity());
		}
		String key = normalizeKey(idempotencyKey);
		ShopOrder order = shopOrderRepository.findByIdempotencyKey(key)
				.map(existing -> reuseReady(existing, buyer, cart.total()))
				.orElseGet(() -> createCartOrder(buyer, rows, cart.total(), key));
		return toPrepare(order, orderName(order));
	}

	@Transactional
	public ProjectPaymentPrepareResponse prepareBuyNow(User buyer, String productId, int quantity,
													   String idempotencyKey) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("수량이 올바르지 않습니다.");
		}
		ShopProductView product = shopService.findProduct(productId)
				.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
		shopService.requirePurchasable(buyer, product);
		if (product.soldOut()) {
			throw new IllegalArgumentException("품절된 상품입니다.");
		}
		String cartKey = productId.contains(":")
				? productId.trim()
				: ShopCheckoutService.cartProductId(Long.parseLong(product.id()),
				product.variants().getFirst().id());
		Long variantId = ShopCheckoutService.parseVariantId(cartKey);
		goodsService.ensureVariantStock(variantId, quantity);
		long amount = (long) product.price() * quantity;
		String key = normalizeKey(idempotencyKey);
		ShopOrder order = shopOrderRepository.findByIdempotencyKey(key)
				.map(existing -> reuseReady(existing, buyer, amount))
				.orElseGet(() -> createBuyNowOrder(buyer, product, cartKey, variantId, quantity, amount, key));
		return toPrepare(order, product.title());
	}

	@Transactional(noRollbackFor = TossPaymentException.class)
	public CommercePaymentResultView confirmVirtualAccount(Long buyerId, String paymentKey,
														   String orderId, Long amount) {
		if (paymentKey == null || paymentKey.isBlank() || orderId == null || amount == null) {
			throw new IllegalArgumentException("결제 정보가 올바르지 않습니다.");
		}
		ShopOrder order = findMyOrderForUpdate(buyerId, orderId);
		if (order.getPaymentStatus() != FanProjectPaymentStatus.READY) {
			if (paymentKey.equals(order.getProviderTransactionId())) {
				order.getItems().size();
				return CommercePaymentResultView.fromShop(order);
			}
			throw new IllegalStateException("이미 처리된 주문입니다.");
		}
		if (!order.getAmount().equals(amount)) {
			throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다.");
		}
		TossPaymentResponse response;
		try {
			response = tossClient.confirm(paymentKey, orderId, amount);
		} catch (TossPaymentException e) {
			order.markFailed();
			throw e;
		}
		try {
			TossVirtualAccountSupport.requireWaitingVirtualAccount(response);
		} catch (TossPaymentException e) {
			order.markFailed();
			throw e;
		}
		TossPaymentResponse.VirtualAccount account = response.virtualAccount();
		order.markWaitingForDeposit(
				response.paymentKey(),
				account.bankCode(),
				account.accountNumber(),
				TossVirtualAccountSupport.toKoreaTime(account.dueDate()),
				response.secret()
		);
		order.getItems().size();
		return CommercePaymentResultView.fromShop(order);
	}

	@Transactional
	public void failOrder(Long buyerId, String orderId) {
		if (orderId == null || orderId.isBlank()) {
			return;
		}
		shopOrderRepository.findByOrderNoForUpdate(orderId)
				.filter(order -> order.getBuyer().getId().equals(buyerId))
				.filter(order -> order.getPaymentStatus() == FanProjectPaymentStatus.READY)
				.ifPresent(ShopOrder::markFailed);
	}

	@Transactional
	public void syncWaitingDeposit(String orderNo) {
		ShopOrder order = shopOrderRepository.findByOrderNoForUpdate(orderNo).orElse(null);
		if (order == null || order.getPaymentStatus() != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
			return;
		}
		syncWithToss(order, LocalDateTime.now());
	}

	@Transactional
	public CommercePaymentStatusView refreshDepositStatus(Long buyerId, String orderNo) {
		ShopOrder order = shopOrderRepository.findByOrderNo(orderNo)
				.orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
		if (!order.getBuyer().getId().equals(buyerId)) {
			throw new AccessDeniedException("본인 주문만 확인할 수 있습니다.");
		}
		if (order.getPaymentStatus() != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
			return CommercePaymentStatusView.from(order.getPaymentStatus());
		}
		ShopOrder locked = findMyOrderForUpdate(buyerId, orderNo);
		if (locked.getPaymentStatus() == FanProjectPaymentStatus.WAITING_FOR_DEPOSIT) {
			syncWithToss(locked, LocalDateTime.now());
		}
		return CommercePaymentStatusView.from(locked.getPaymentStatus());
	}

	@Transactional
	public void failStaleReadyOrder(String orderNo) {
		shopOrderRepository.findByOrderNoForUpdate(orderNo)
				.filter(order -> order.getPaymentStatus() == FanProjectPaymentStatus.READY)
				.ifPresent(ShopOrder::markFailed);
	}

	private void syncWithToss(ShopOrder order, LocalDateTime now) {
		TossPaymentResponse payment;
		try {
			payment = tossClient.getPayment(order.getProviderTransactionId());
		} catch (TossPaymentException e) {
			log.warn("[샵 결제] 토스 조회 실패. orderNo={}, code={}", order.getOrderNo(), e.getCode());
			return;
		}
		switch (payment.status()) {
			case "DONE" -> completePayment(order, now);
			case "CANCELED", "PARTIAL_CANCELED", "EXPIRED" -> order.expire(now);
			default -> {
				if (order.getDueDate() != null && order.getDueDate().isBefore(now)) {
					order.expire(now);
				}
			}
		}
	}

	private void completePayment(ShopOrder order, LocalDateTime paidAt) {
		if (!order.markPaid(paidAt)) {
			return;
		}
		order.getItems().size();
		for (ShopOrderItem item : order.getItems()) {
			goodsService.decreaseVariantStock(item.getVariantId(), item.getQuantity());
		}
		if (order.getSource() == ShopOrderSource.CART) {
			List<ShopCartItem> cart = shopCartItemRepository.findByUserOrderByCreatedAtAsc(order.getBuyer());
			List<String> productIds = order.getItems().stream().map(ShopOrderItem::getProductId).toList();
			List<ShopCartItem> remove = cart.stream()
					.filter(row -> productIds.contains(row.getProductId()))
					.toList();
			if (!remove.isEmpty()) {
				shopCartItemRepository.deleteAll(remove);
			}
		}
		log.info("[샵 결제] 입금 확인 완료. orderNo={}", order.getOrderNo());
	}

	private ShopOrder createCartOrder(User buyer, List<ShopCartItem> rows, int total, String key) {
		LocalDateTime now = LocalDateTime.now();
		ShopOrder order = ShopOrder.createReady(
				buyer,
				TossVirtualAccountSupport.newOrderNo("GD", buyer.getId(), now),
				key,
				(long) total,
				ShopOrderSource.CART
		);
		for (ShopCartItem row : rows) {
			ShopProductView product = shopService.findProduct(row.getProductId())
					.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
			order.addItem(ShopOrderItem.of(
					order,
					parseGoodsId(row.getProductId()),
					ShopCheckoutService.parseVariantId(row.getProductId()),
					row.getProductId(),
					product.title(),
					row.getQuantity(),
					row.getUnitPrice()));
		}
		return shopOrderRepository.save(order);
	}

	private ShopOrder createBuyNowOrder(User buyer, ShopProductView product, String productId,
										Long variantId, int quantity, long amount, String key) {
		LocalDateTime now = LocalDateTime.now();
		ShopOrder order = ShopOrder.createReady(
				buyer,
				TossVirtualAccountSupport.newOrderNo("GD", buyer.getId(), now),
				key,
				amount,
				ShopOrderSource.BUY_NOW
		);
		order.addItem(ShopOrderItem.of(
				order,
				Long.parseLong(product.id()),
				variantId,
				productId,
				product.title(),
				quantity,
				product.price()
		));
		return shopOrderRepository.save(order);
	}

	private ShopOrder reuseReady(ShopOrder existing, User buyer, long amount) {
		boolean same = existing.getBuyer().getId().equals(buyer.getId())
				&& existing.getAmount().equals(amount)
				&& existing.getPaymentStatus() == FanProjectPaymentStatus.READY;
		if (!same) {
			throw new IllegalStateException("이미 사용된 결제 요청입니다. 다시 시도해주세요.");
		}
		return existing;
	}

	private ProjectPaymentPrepareResponse toPrepare(ShopOrder order, String orderName) {
		String customer = order.getBuyer().getNickname() != null
				? order.getBuyer().getNickname()
				: "WePlaNet 회원";
		return new ProjectPaymentPrepareResponse(
				true,
				tossProperties.clientKey(),
				order.getOrderNo(),
				orderName,
				order.getAmount(),
				customer,
				TossVirtualAccountSupport.VALID_HOURS,
				"결제창을 여는 중입니다."
		);
	}

	private ShopOrder findMyOrderForUpdate(Long buyerId, String orderId) {
		ShopOrder order = shopOrderRepository.findByOrderNoForUpdate(orderId)
				.orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
		if (!order.getBuyer().getId().equals(buyerId)) {
			throw new AccessDeniedException("본인 주문만 결제할 수 있습니다.");
		}
		return order;
	}

	private static String orderName(ShopOrder order) {
		if (order.getItems().isEmpty()) {
			return "굿즈 주문";
		}
		String first = order.getItems().getFirst().getProductName();
		return order.getItems().size() > 1 ? first + " 외 " + (order.getItems().size() - 1) + "건" : first;
	}

	private static String normalizeKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return UUID.randomUUID().toString();
		}
		return idempotencyKey.trim();
	}

	private static Long parseGoodsId(String productId) {
		int sep = productId.indexOf(':');
		try {
			return Long.parseLong(sep > 0 ? productId.substring(0, sep) : productId);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
		}
	}
}
