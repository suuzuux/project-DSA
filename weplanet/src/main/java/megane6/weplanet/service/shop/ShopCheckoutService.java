package megane6.weplanet.service.shop;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ShopProductView;
import megane6.weplanet.domain.entity.ShopCartItem;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.ShopCartItemRepository;
import megane6.weplanet.service.ShopService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 장바구니 모의 결제 (현재는 parseVariantId·cartProductId 만 공용으로 사용). */
@Service
@RequiredArgsConstructor
public class ShopCheckoutService {

	private final ShopCartItemRepository shopCartItemRepository;
	private final GoodsService goodsService;
	private final ShopService shopService;
	private final MessageSource messageSource;

	// 화면 언어 에러 메시지 조회
	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	@Transactional
	public int checkout(User user) {
		List<ShopCartItem> items = shopCartItemRepository.findByUserOrderByCreatedAtAsc(user);
		if (items.isEmpty()) {
			throw new IllegalArgumentException(msg("shop.error.cartEmpty"));
		}
		for (ShopCartItem item : items) {
			ShopProductView product = shopService.findProduct(item.getProductId())
					.orElseThrow(() -> new IllegalArgumentException(msg("shop.error.productNotFound")));
			shopService.requirePurchasable(user, product);
			Long variantId = parseVariantId(item.getProductId());
			goodsService.decreaseVariantStock(variantId, item.getQuantity());
		}
		shopCartItemRepository.deleteAll(items);
		return items.size();
	}

	@Transactional
	public void buyNow(User user, String productId, int quantity) {
		ShopProductView product = shopService.findProduct(productId)
				.orElseThrow(() -> new IllegalArgumentException(msg("shop.error.productNotFound")));
		shopService.requirePurchasable(user, product);
		Long variantId = parseVariantId(productId);
		goodsService.decreaseVariantStock(variantId, quantity);
	}

	/** 장바구니 productId: "{goodsId}:{variantId}" */
	public Long parseVariantId(String productId) {
		if (productId == null || productId.isBlank()) {
			throw new IllegalArgumentException(msg("shop.error.productNotFound"));
		}
		String raw = productId.trim();
		int sep = raw.indexOf(':');
		try {
			if (sep > 0) {
				return Long.parseLong(raw.substring(sep + 1));
			}
			throw new IllegalArgumentException(msg("shop.error.optionRequired"));
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException(msg("shop.error.productNotFound"));
		}
	}

	public static String cartProductId(Long goodsId, Long variantId) {
		return goodsId + ":" + variantId;
	}
}
