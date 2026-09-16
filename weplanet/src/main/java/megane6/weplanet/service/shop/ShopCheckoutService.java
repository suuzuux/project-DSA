package megane6.weplanet.service.shop;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.ShopCartItem;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.ShopCartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장바구니 결제(모의 주문 완료). 재고 차감과 장바구니 비우기를 한 트랜잭션에서 처리한다.
 * 별도 Order 엔티티는 아직 없어, 이 체크아웃이 구매 완료 진입점이다.
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
			Long goodsId = parseGoodsId(item.getProductId());
			goodsService.decreaseStock(goodsId, item.getQuantity());
		}
		shopCartItemRepository.deleteAll(items);
		return items.size();
	}

	@Transactional
	public void buyNow(User user, String productId, int quantity) {
		Long goodsId = parseGoodsId(productId);
		goodsService.decreaseStock(goodsId, quantity);
	}

	private static Long parseGoodsId(String productId) {
		if (productId == null || productId.isBlank()) {
			throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
		}
		try {
			return Long.parseLong(productId.trim());
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
		}
	}
}
