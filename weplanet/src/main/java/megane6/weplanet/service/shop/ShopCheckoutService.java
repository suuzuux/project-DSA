package megane6.weplanet.service.shop;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.ShopCartItem;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.ShopCartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장바구니 결제(모의). Variant 재고 차감과 장바구니 비우기를 한 트랜잭션에서 처리.
 */
@Service
@RequiredArgsConstructor
public class ShopCheckoutService {

	private final ShopCartItemRepository shopCartItemRepository;
	private final GoodsService goodsService;

	@Transactional
	public int checkout(User user) {
		List<ShopCartItem> items = shopCartItemRepository.findByUserOrderByCreatedAtAsc(user);
		if (items.isEmpty()) {
			throw new IllegalArgumentException("장바구니가 비어 있습니다.");
		}
		for (ShopCartItem item : items) {
			Long variantId = parseVariantId(item.getProductId());
			goodsService.decreaseVariantStock(variantId, item.getQuantity());
		}
		shopCartItemRepository.deleteAll(items);
		return items.size();
	}

	@Transactional
	public void buyNow(User user, String productId, int quantity) {
		Long variantId = parseVariantId(productId);
		goodsService.decreaseVariantStock(variantId, quantity);
	}

	/** 장바구니 productId: "{goodsId}:{variantId}" */
	public static Long parseVariantId(String productId) {
		if (productId == null || productId.isBlank()) {
			throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
		}
		String raw = productId.trim();
		int sep = raw.indexOf(':');
		try {
			if (sep > 0) {
				return Long.parseLong(raw.substring(sep + 1));
			}
			throw new IllegalArgumentException("옵션을 선택해주세요.");
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
		}
	}

	public static String cartProductId(Long goodsId, Long variantId) {
		return goodsId + ":" + variantId;
	}
}
