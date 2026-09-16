package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ShopCartItemView;
import megane6.weplanet.domain.dto.ShopCartSummaryView;
import megane6.weplanet.domain.dto.ShopProductView;
import megane6.weplanet.domain.entity.ShopCartItem;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.ShopCartItemRepository;
import megane6.weplanet.service.shop.GoodsService;
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
		if (product.soldOut()) {
			throw new IllegalArgumentException("재고 부족");
		}
		ShopCartItem existing = shopCartItemRepository.findByUserAndProductId(user, productId).orElse(null);
		int nextQty = existing != null ? existing.getQuantity() + quantity : quantity;
		goodsService.ensureEnoughStock(parseGoodsId(productId), nextQty);
		if (existing != null) {
			existing.setQuantity(nextQty);
			shopCartItemRepository.save(existing);
			return;
		}
		shopCartItemRepository.save(ShopCartItem.builder()
				.user(user)
				.productId(product.id())
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
		goodsService.ensureEnoughStock(parseGoodsId(item.getProductId()), quantity);
		item.setQuantity(quantity);
		shopCartItemRepository.save(item);
	}

	@Transactional
	public void removeItem(User user, Long itemId) {
		ShopCartItem item = getOwnedItem(user, itemId);
		shopCartItemRepository.delete(item);
	}

	private ShopCartItem getOwnedItem(User user, Long itemId) {
		return shopCartItemRepository.findByIdAndUser(itemId, user)
				.orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다."));
	}

	private static Long parseGoodsId(String productId) {
		try {
			return Long.parseLong(productId.trim());
		} catch (Exception ex) {
			throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
		}
	}
}
