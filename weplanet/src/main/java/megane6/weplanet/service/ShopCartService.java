package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ShopCartItemView;
import megane6.weplanet.domain.dto.ShopCartSummaryView;
import megane6.weplanet.domain.dto.ShopProductView;
import megane6.weplanet.domain.dto.ShopVariantView;
import megane6.weplanet.domain.entity.ShopCartItem;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.ShopCartItemRepository;
import megane6.weplanet.service.shop.GoodsService;
import megane6.weplanet.service.shop.ShopCheckoutService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopCartService {

	private static final int SHIPPING_FEE = 3_000;

	private final ShopCartItemRepository shopCartItemRepository;
	private final ShopService shopService;
	private final GoodsService goodsService;
	private final ShopCheckoutService shopCheckoutService;
	private final MessageSource messageSource;

	// 화면 언어 에러 메시지 조회
	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	@Transactional(readOnly = true)
	public ShopCartSummaryView getCartSummary(User user) {
		List<ShopCartItem> rows = shopCartItemRepository.findByUserOrderByCreatedAtAsc(user);
		List<ShopCartItemView> items = new ArrayList<>();
		int subtotal = 0;
		for (ShopCartItem row : rows) {
			ShopProductView product = shopService.findProduct(row.getProductId()).orElse(null);
			if (product == null) {
				continue;
			}
			int lineTotal = row.getUnitPrice() * row.getQuantity();
			subtotal += lineTotal;
			items.add(new ShopCartItemView(
					row.getId(),
					product,
					row.getQuantity(),
					row.getUnitPrice(),
					lineTotal
			));
		}
		int shippingFee = subtotal > 0 ? SHIPPING_FEE : 0;
		return new ShopCartSummaryView(items, subtotal, shippingFee, subtotal + shippingFee);
	}

	@Transactional(readOnly = true)
	public long countItems(User user) {
		return shopCartItemRepository.countByUser(user);
	}

	@Transactional
	public void addItem(User user, String productId, int quantity) {
		if (quantity <= 0) {
			throw new IllegalArgumentException(msg("shop.error.invalidQuantity"));
		}
		ShopProductView product = shopService.findProduct(productId)
				.orElseThrow(() -> new IllegalArgumentException(msg("shop.error.productNotFound")));
		shopService.requirePurchasable(user, product);
		if (product.soldOut()) {
			throw new IllegalArgumentException(msg("shop.error.soldOut"));
		}
		String cartKey = resolveCartProductId(productId, product);
		Long variantId = shopCheckoutService.parseVariantId(cartKey);
		int nextQty;
		ShopCartItem existing = shopCartItemRepository.findByUserAndProductId(user, cartKey).orElse(null);
		nextQty = existing != null ? existing.getQuantity() + quantity : quantity;
		goodsService.ensureVariantStock(variantId, nextQty);
		if (existing != null) {
			existing.setQuantity(nextQty);
			shopCartItemRepository.save(existing);
			return;
		}
		shopCartItemRepository.save(ShopCartItem.builder()
				.user(user)
				.productId(cartKey)
				.quantity(quantity)
				.unitPrice(product.price())
				.build());
	}

	@Transactional
	public void updateQuantity(User user, Long itemId, int quantity) {
		if (quantity <= 0) {
			throw new IllegalArgumentException(msg("shop.error.invalidQuantity"));
		}
		ShopCartItem item = getOwnedItem(user, itemId);
		Long variantId = shopCheckoutService.parseVariantId(item.getProductId());
		try {
			goodsService.ensureVariantStock(variantId, quantity);
		} catch (IllegalArgumentException e) {
			// 번역된 재고 부족 문구와 비교한다 (언어와 무관하게 판단).
			if (msg("shop.error.outOfStock").equals(e.getMessage())) {
				throw new IllegalArgumentException(msg("shop.error.noStock"));
			}
			throw e;
		}
		item.setQuantity(quantity);
		shopCartItemRepository.save(item);
	}

	@Transactional
	public void removeItem(User user, Long itemId) {
		ShopCartItem item = getOwnedItem(user, itemId);
		shopCartItemRepository.delete(item);
	}

	private String resolveCartProductId(String productId, ShopProductView product) {
		if (productId != null && productId.contains(":")) {
			return productId.trim();
		}
		List<ShopVariantView> available = product.variants().stream()
				.filter(v -> !v.soldOut())
				.toList();
		if (available.isEmpty()) {
			throw new IllegalArgumentException(msg("shop.error.soldOut"));
		}
		boolean hasSelectable = available.stream().anyMatch(ShopVariantView::selectable);
		if (hasSelectable && available.stream().filter(ShopVariantView::selectable).count() > 1) {
			throw new IllegalArgumentException(msg("shop.error.optionRequired"));
		}
		ShopVariantView pick = hasSelectable
				? available.stream().filter(ShopVariantView::selectable).findFirst().orElse(available.getFirst())
				: available.getFirst();
		return ShopCheckoutService.cartProductId(Long.parseLong(product.id()), pick.id());
	}

	private ShopCartItem getOwnedItem(User user, Long itemId) {
		return shopCartItemRepository.findByIdAndUser(itemId, user)
				.orElseThrow(() -> new IllegalArgumentException(msg("shop.error.cartItemNotFound")));
	}
}
