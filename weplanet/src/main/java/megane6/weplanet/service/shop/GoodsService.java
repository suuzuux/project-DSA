package megane6.weplanet.service.shop;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Goods;
import megane6.weplanet.domain.entity.GoodsCategoryLink;
import megane6.weplanet.domain.entity.GoodsOption;
import megane6.weplanet.domain.entity.GoodsVariant;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.GoodsCategoryType;
import megane6.weplanet.domain.entity.enumfolder.GoodsShopCategory;
import megane6.weplanet.domain.entity.enumfolder.GoodsStatus;
import megane6.weplanet.repository.GoodsRepository;
import megane6.weplanet.repository.GoodsVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class GoodsService {

	private final GoodsRepository goodsRepository;
	private final GoodsVariantRepository goodsVariantRepository;
	private final ShopImageStorage shopImageStorage;

	@Transactional(readOnly = true)
	public List<Goods> listForArtist(User artist) {
		List<Goods> list = goodsRepository.findByArtistAndDeletedAtIsNullOrderBySortOrderAscIdAsc(artist);
		for (Goods goods : list) {
			goods.getCategoryLinks().size();
			goods.getVariants().size();
		}
		return list;
	}

	@Transactional(readOnly = true)
	public Goods getOwned(User artist, Long goodsId) {
		Goods goods = goodsRepository.findByIdAndArtistAndDeletedAtIsNull(goodsId, artist)
				.orElseThrow(() -> new IllegalArgumentException("굿즈를 찾을 수 없습니다."));
		goods.getCategoryLinks().size();
		goods.getOptions().size();
		goods.getVariants().size();
		return goods;
	}

	@Transactional(readOnly = true)
	public GoodsCategoryOptionsPayload categoryOptionsPayload(Goods goods) {
		return GoodsCategoryOptionsPayload.fromEntity(
				goods.getCategories(), goods.getVariants(), goods.getOptions());
	}

	public Goods create(User artist,
						String name,
						String description,
						int price,
						String officialUrl,
						GoodsStatus status,
						GoodsShopCategory shopCategory,
						MultipartFile thumbnail,
						GoodsCategoryOptionsPayload categoryOptions) {
		requireArtist(artist);
		categoryOptions.validate();
		String stored = shopImageStorage.storeImage(thumbnail);
		List<Goods> existing = listForArtist(artist);
		int nextOrder = existing.isEmpty()
				? 0
				: existing.stream().mapToInt(Goods::getSortOrder).max().orElse(-1) + 1;
		GoodsShopCategory resolved = GoodsShopCategory.fromOrDefault(shopCategory);
		Goods goods = Goods.builder()
				.artist(artist)
				.name(name.trim())
				.description(blankToNull(description))
				.price(price)
				.thumbnailUrl(stored)
				.officialUrl(blankToNull(officialUrl))
				.status(status != null ? status : GoodsStatus.ON_SALE)
				.sortOrder(nextOrder)
				.shopCategory(resolved)
				.membershipOnly(resolved.isMembershipOnly())
				.build();
		applyPayload(goods, categoryOptions);
		return goodsRepository.save(goods);
	}

	public Goods update(User artist,
						Long goodsId,
						String name,
						String description,
						int price,
						String officialUrl,
						GoodsStatus status,
						GoodsShopCategory shopCategory,
						MultipartFile thumbnail,
						GoodsCategoryOptionsPayload categoryOptions) {
		categoryOptions.validate();
		Goods goods = getOwned(artist, goodsId);
		GoodsShopCategory resolved = GoodsShopCategory.fromOrDefault(shopCategory);
		goods.setName(name.trim());
		goods.setDescription(blankToNull(description));
		goods.setPrice(price);
		goods.setOfficialUrl(blankToNull(officialUrl));
		goods.setShopCategory(resolved);
		goods.setMembershipOnly(resolved.isMembershipOnly());
		if (status != null) {
			goods.setStatus(status);
		}
		if (thumbnail != null && !thumbnail.isEmpty()) {
			String previous = goods.getThumbnailUrl();
			goods.setThumbnailUrl(shopImageStorage.storeImage(thumbnail));
			shopImageStorage.delete(previous);
		}
		// orphanRemoval clear → 동일 유니크키로 재삽입 시 INSERT가 DELETE보다 먼저 나가면
		// uk_goods_variant / uk_goods_category 충돌로 수정이 실패한다. flush로 삭제를 먼저 확정.
		goods.getCategoryLinks().clear();
		goods.getOptions().clear();
		goods.getVariants().clear();
		goodsRepository.flush();
		applyPayload(goods, categoryOptions);
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
	public void ensureVariantStock(Long variantId, int quantity) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("수량이 올바르지 않습니다.");
		}
		GoodsVariant variant = goodsVariantRepository.findById(variantId)
				.orElseThrow(() -> new IllegalArgumentException("상품 옵션을 찾을 수 없습니다."));
		if (variant.getStockQuantity() < quantity) {
			throw new IllegalArgumentException("재고 부족");
		}
	}

	public void decreaseVariantStock(Long variantId, int quantity) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("수량이 올바르지 않습니다.");
		}
		GoodsVariant variant = goodsVariantRepository.findByIdForUpdate(variantId)
				.orElseThrow(() -> new IllegalArgumentException("상품 옵션을 찾을 수 없습니다."));
		Goods goods = variant.getGoods();
		if (goods.isDeleted() || goods.getStatus() != GoodsStatus.ON_SALE) {
			throw new IllegalArgumentException("판매 중이 아닌 상품입니다.");
		}
		if (variant.getStockQuantity() < quantity) {
			throw new IllegalArgumentException("재고 부족");
		}
		variant.setStockQuantity(variant.getStockQuantity() - quantity);
		goodsVariantRepository.save(variant);
	}

	public String storeEditorImage(MultipartFile file) {
		return shopImageStorage.storeImage(file);
	}

	private void applyPayload(Goods goods, GoodsCategoryOptionsPayload payload) {
		Set<GoodsCategoryType> selected = payload.selectedSet();
		for (GoodsCategoryType cat : selected) {
			goods.getCategoryLinks().add(GoodsCategoryLink.builder()
					.goods(goods)
					.category(cat)
					.build());
		}

		boolean hasSelectable = selected.stream().anyMatch(GoodsCategoryType::isHasSelectableOptions);
		boolean needsDefault = selected.isEmpty()
				|| selected.stream().anyMatch(c -> !c.isHasSelectableOptions());

		if (selected.contains(GoodsCategoryType.CLOTHING)) {
			payload.sizeMap("CLOTHING").forEach((size, stock) ->
					goods.getVariants().add(GoodsVariant.builder()
							.goods(goods)
							.optionKey(GoodsVariant.KEY_SIZE)
							.optionValue(size)
							.stockQuantity(stock)
							.build()));
		}
		if (selected.contains(GoodsCategoryType.SHOES)) {
			payload.sizeMap("SHOES").forEach((mm, stock) ->
					goods.getVariants().add(GoodsVariant.builder()
							.goods(goods)
							.optionKey(GoodsVariant.KEY_SHOE_MM)
							.optionValue(mm)
							.stockQuantity(stock)
							.build()));
		}

		if (needsDefault) {
			boolean onlySelectable = !selected.isEmpty()
					&& selected.stream().allMatch(GoodsCategoryType::isHasSelectableOptions);
			if (!onlySelectable || !hasSelectable) {
				goods.getVariants().add(GoodsVariant.builder()
						.goods(goods)
						.optionKey(GoodsVariant.KEY_DEFAULT)
						.optionValue("")
						.stockQuantity(payload.defaultStock())
						.build());
			}
		}

		if (goods.getVariants().isEmpty()) {
			goods.getVariants().add(GoodsVariant.builder()
					.goods(goods)
					.optionKey(GoodsVariant.KEY_DEFAULT)
					.optionValue("")
					.stockQuantity(payload.defaultStock())
					.build());
		}

		if (selected.contains(GoodsCategoryType.BAG)) {
			Map<String, String> bag = payload.attributes().getOrDefault("BAG", Map.of());
			addAttr(goods, GoodsCategoryType.BAG, "WIDTH", bag.get("width"));
			addAttr(goods, GoodsCategoryType.BAG, "HEIGHT", bag.get("height"));
			addAttr(goods, GoodsCategoryType.BAG, "DEPTH", bag.get("depth"));
		}
		for (GoodsCategoryType cat : List.of(GoodsCategoryType.ACCESSORY, GoodsCategoryType.OTHER)) {
			if (!selected.contains(cat)) {
				continue;
			}
			String note = payload.attributes().getOrDefault(cat.name(), Map.of()).get("note");
			if (note != null && !note.isBlank()) {
				addAttr(goods, cat, "NOTE", note);
			}
		}
	}

	private static void addAttr(Goods goods, GoodsCategoryType cat, String key, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		goods.getOptions().add(GoodsOption.builder()
				.goods(goods)
				.category(cat)
				.optionKey(key)
				.optionValue(value.trim())
				.build());
	}

	private static void requireArtist(User artist) {
		if (artist == null) {
			throw new IllegalArgumentException("아티스트를 선택해주세요.");
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
