package megane6.weplanet.service.shop;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Goods;
import megane6.weplanet.domain.entity.GoodsCategoryLink;
import megane6.weplanet.domain.entity.GoodsOption;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.GoodsCategoryType;
import megane6.weplanet.domain.entity.enumfolder.GoodsStatus;
import megane6.weplanet.repository.GoodsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class GoodsService {

	private final GoodsRepository goodsRepository;
	private final ShopImageStorage shopImageStorage;

	@Transactional(readOnly = true)
	public List<Goods> listForArtist(User artist) {
		List<Goods> list = goodsRepository.findByArtistAndDeletedAtIsNullOrderBySortOrderAscIdAsc(artist);
		// 뷰 렌더링에서 카테고리 접근 시 LazyInitializationException 방지
		for (Goods goods : list) {
			goods.getCategoryLinks().size();
		}
		return list;
	}

	@Transactional(readOnly = true)
	public Goods getOwned(User artist, Long goodsId) {
		Goods goods = goodsRepository.findByIdAndArtistAndDeletedAtIsNull(goodsId, artist)
				.orElseThrow(() -> new IllegalArgumentException("굿즈를 찾을 수 없습니다."));
		// lazy associations for form
		goods.getCategoryLinks().size();
		goods.getOptions().size();
		return goods;
	}

	@Transactional(readOnly = true)
	public GoodsCategoryOptionsPayload categoryOptionsPayload(Goods goods) {
		return GoodsCategoryOptionsPayload.fromEntity(goods.getCategories(), goods.getOptions());
	}

	public Goods create(User artist,
						String name,
						String description,
						int price,
						int stockQuantity,
						String officialUrl,
						GoodsStatus status,
						MultipartFile thumbnail,
						GoodsCategoryOptionsPayload categoryOptions) {
		requireArtist(artist);
		requireNonNegativeStock(stockQuantity);
		categoryOptions.validate();
		String stored = shopImageStorage.storeImage(thumbnail);
		List<Goods> existing = listForArtist(artist);
		int nextOrder = existing.isEmpty()
				? 0
				: existing.stream().mapToInt(Goods::getSortOrder).max().orElse(-1) + 1;
		Goods goods = Goods.builder()
				.artist(artist)
				.name(name.trim())
				.description(blankToNull(description))
				.price(price)
				.thumbnailUrl(stored)
				.officialUrl(blankToNull(officialUrl))
				.status(status != null ? status : GoodsStatus.ON_SALE)
				.sortOrder(nextOrder)
				.stockQuantity(stockQuantity)
				.build();
		applyCategoryOptions(goods, categoryOptions);
		return goodsRepository.save(goods);
	}

	public Goods update(User artist,
						Long goodsId,
						String name,
						String description,
						int price,
						int stockQuantity,
						String officialUrl,
						GoodsStatus status,
						MultipartFile thumbnail,
						GoodsCategoryOptionsPayload categoryOptions) {
		requireNonNegativeStock(stockQuantity);
		categoryOptions.validate();
		Goods goods = getOwned(artist, goodsId);
		goods.setName(name.trim());
		goods.setDescription(blankToNull(description));
		goods.setPrice(price);
		goods.setStockQuantity(stockQuantity);
		goods.setOfficialUrl(blankToNull(officialUrl));
		if (status != null) {
			goods.setStatus(status);
		}
		if (thumbnail != null && !thumbnail.isEmpty()) {
			String previous = goods.getThumbnailUrl();
			goods.setThumbnailUrl(shopImageStorage.storeImage(thumbnail));
			shopImageStorage.delete(previous);
		}
		goods.getCategoryLinks().clear();
		goods.getOptions().clear();
		applyCategoryOptions(goods, categoryOptions);
		return goodsRepository.save(goods);
	}

	public void softDelete(User artist, Long goodsId) {
		Goods goods = getOwned(artist, goodsId);
		goods.softDelete();
		goodsRepository.save(goods);
	}

	public void reorder(User artist, List<Long> orderedIds) {
		if (orderedIds == null || orderedIds.isEmpty()) {
			return;
		}
		List<Goods> owned = listForArtist(artist);
		Set<Long> ownedIds = new LinkedHashSet<>();
		for (Goods g : owned) {
			ownedIds.add(g.getId());
		}
		if (orderedIds.size() != ownedIds.size() || !ownedIds.containsAll(orderedIds)) {
			throw new IllegalArgumentException("순서를 저장할 수 없습니다. 목록을 새로고침 후 다시 시도해주세요.");
		}
		int order = 0;
		for (Long id : orderedIds) {
			for (Goods goods : owned) {
				if (goods.getId().equals(id)) {
					goods.setSortOrder(order++);
					goodsRepository.save(goods);
					break;
				}
			}
		}
	}

	@Transactional(readOnly = true)
	public void ensureEnoughStock(Long goodsId, int quantity) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("수량이 올바르지 않습니다.");
		}
		Goods goods = goodsRepository.findByIdAndDeletedAtIsNull(goodsId)
				.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
		if (goods.getStatus() != GoodsStatus.ON_SALE) {
			throw new IllegalArgumentException("판매 중이 아닌 상품입니다.");
		}
		if (goods.getStockQuantity() < quantity) {
			throw new IllegalArgumentException("재고 부족");
		}
	}

	public void decreaseStock(Long goodsId, int quantity) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("수량이 올바르지 않습니다.");
		}
		Goods goods = goodsRepository.findByIdAndDeletedAtIsNullForUpdate(goodsId)
				.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
		if (goods.getStatus() != GoodsStatus.ON_SALE) {
			throw new IllegalArgumentException("판매 중이 아닌 상품입니다.");
		}
		if (goods.getStockQuantity() < quantity) {
			throw new IllegalArgumentException("재고 부족");
		}
		goods.setStockQuantity(goods.getStockQuantity() - quantity);
		goodsRepository.save(goods);
	}

	public String storeEditorImage(MultipartFile file) {
		return shopImageStorage.storeImage(file);
	}

	private void applyCategoryOptions(Goods goods, GoodsCategoryOptionsPayload payload) {
		Set<GoodsCategoryType> unique = new LinkedHashSet<>(payload.categories());
		for (GoodsCategoryType category : unique) {
			GoodsCategoryLink link = GoodsCategoryLink.builder()
					.goods(goods)
					.category(category)
					.build();
			goods.getCategoryLinks().add(link);
		}
		for (Map.Entry<GoodsCategoryType, Map<String, List<String>>> catEntry : payload.optionsByCategory().entrySet()) {
			GoodsCategoryType category = catEntry.getKey();
			if (!unique.contains(category)) {
				continue;
			}
			for (Map.Entry<String, List<String>> optEntry : catEntry.getValue().entrySet()) {
				String key = mapOptionKey(category, optEntry.getKey());
				for (String value : optEntry.getValue()) {
					if (value == null || value.isBlank()) {
						continue;
					}
					goods.getOptions().add(GoodsOption.builder()
							.goods(goods)
							.category(category)
							.optionKey(key)
							.optionValue(value.trim())
							.build());
				}
			}
		}
	}

	private static String mapOptionKey(GoodsCategoryType category, String jsKey) {
		return switch (category) {
			case CLOTHING -> "SIZE";
			case SHOES -> "SHOE_MM";
			case BAG -> switch (jsKey) {
				case "width" -> "WIDTH";
				case "height" -> "HEIGHT";
				case "depth" -> "DEPTH";
				default -> jsKey.toUpperCase();
			};
			case ACCESSORY, OTHER -> "NOTE";
		};
	}

	private static void requireArtist(User artist) {
		if (artist == null) {
			throw new IllegalArgumentException("아티스트를 선택해주세요.");
		}
	}

	private static void requireNonNegativeStock(int stockQuantity) {
		if (stockQuantity < 0) {
			throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
		}
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
