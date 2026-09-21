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
			throw new IllegalArgumentException("수량이 올바르지 않습니다.");
		}
		ShopProductView product = shopService.findProduct(productId)
				.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
		shopService.requirePurchasable(user, product);
		if (product.soldOut()) {
			throw new IllegalArgumentException("품절된 상품입니다.");
		}
		String cartKey = resolveCartProductId(productId, product);
		Long variantId = ShopCheckoutService.parseVariantId(cartKey);
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
			throw new IllegalArgumentException("수량이 올바르지 않습니다.");
		}
		ShopCartItem item = getOwnedItem(user, itemId);
		Long variantId = ShopCheckoutService.parseVariantId(item.getProductId());
		goodsService.ensureVariantStock(variantId, quantity);
		item.setQuantity(quantity);
		shopCartItemRepository.save(item);
	}

	@Transactional
	public void removeItem(User user, Long itemId) {
		ShopCartItem item = getOwnedItem(user, itemId);
		shopCartItemRepository.delete(item);
	}

	private static String resolveCartProductId(String productId, ShopProductView product) {
		if (productId != null && productId.contains(":")) {
			return productId.trim();
		}
		List<ShopVariantView> available = product.variants().stream()
				.filter(v -> !v.soldOut())
				.toList();
		if (available.isEmpty()) {
			throw new IllegalArgumentException("품절된 상품입니다.");
		}
		boolean hasSelectable = available.stream().anyMatch(ShopVariantView::selectable);
		if (hasSelectable && available.stream().filter(ShopVariantView::selectable).count() > 1) {
			throw new IllegalArgumentException("옵션을 선택해주세요.");
		}
		ShopVariantView pick = hasSelectable
				? available.stream().filter(ShopVariantView::selectable).findFirst().orElse(available.getFirst())
				: available.getFirst();
		return ShopCheckoutService.cartProductId(Long.parseLong(product.id()), pick.id());
	}

	private ShopCartItem getOwnedItem(User user, Long itemId) {
		return shopCartItemRepository.findByIdAndUser(itemId, user)
				.orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다."));
	}
}
