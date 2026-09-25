package megane6.weplanet.service;

import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ShopProductView;
import megane6.weplanet.domain.dto.ShopVariantView;
import megane6.weplanet.domain.entity.Goods;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.GoodsShopCategory;
import megane6.weplanet.domain.entity.enumfolder.GoodsStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.GoodsRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ShopService {

	public static final String MEMBERSHIP_ONLY_MESSAGE = "멤버십 전용 굿즈입니다.";

	private final UserRepository userRepository;
	private final GoodsRepository goodsRepository;
	private final PortalManagementService portalManagementService;
	private final MembershipService membershipService;

	public ShopService(UserRepository userRepository,
					   GoodsRepository goodsRepository,
					   PortalManagementService portalManagementService,
					   MembershipService membershipService) {
		this.userRepository = userRepository;
		this.goodsRepository = goodsRepository;
		this.portalManagementService = portalManagementService;
		this.membershipService = membershipService;
	}

	/**
	 * 멤버십 전용 상품은 노출은 하되, 담기/구매는 활성 멤버만 허용.
	 */
	public void requirePurchasable(User buyer, ShopProductView product) {
		if (product == null || !product.membershipOnly()) {
			return;
		}
		User artist = userRepository.findById(product.artistId())
				.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
		if (!membershipService.isActiveMember(buyer, artist)) {
			throw new IllegalArgumentException(MEMBERSHIP_ONLY_MESSAGE);
		}
	}

	public List<ArtistCardView> getShopArtists() {
		List<User> artists = userRepository.findByRole(Role.ARTIST);
		Map<Long, String> logos = portalManagementService.logoImageUrlsByArtistIds(
				artists.stream().map(User::getId).toList());
		return artists.stream()
				.map(user -> ArtistCardView.from(user, logos.get(user.getId())))
				.toList();
	}

	public Optional<ArtistCardView> findArtist(Long artistId) {
		return userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.map(user -> ArtistCardView.from(user, portalManagementService.findLogoImageUrl(user)));
	}

	public List<ShopProductView> getProducts(Long artistId) {
		List<Goods> goods = artistId == null
				? goodsRepository.findByStatusAndDeletedAtIsNullOrderBySortOrderAscIdAsc(GoodsStatus.ON_SALE)
				: goodsRepository.findByArtist_IdAndStatusAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
				artistId, GoodsStatus.ON_SALE);
		for (Goods g : goods) {
			g.getVariants().size();
			g.getCategoryLinks().size();
		}
		Map<Long, String> logos = portalManagementService.logoImageUrlsByArtistIds(
				goods.stream().map(g -> g.getArtist().getId()).distinct().toList());
		return goods.stream().map(g -> toView(g, logos.get(g.getArtist().getId()))).toList();
	}

	public Optional<ShopProductView> findProduct(String productId) {
		Long id = parseGoodsId(productId);
		if (id == null) {
			return Optional.empty();
		}
		return goodsRepository.findByIdAndDeletedAtIsNull(id)
				.filter(g -> g.getStatus() == GoodsStatus.ON_SALE)
				.map(g -> {
					g.getVariants().size();
					g.getCategoryLinks().size();
					return toView(g, portalManagementService.findLogoImageUrl(g.getArtist()));
				});
	}

	public List<ShopProductView> getRecommendedProducts(Collection<String> excludeProductIds, int limit) {
		List<ShopProductView> all = getProducts(null);
		Set<String> exclude = excludeProductIds == null
				? Set.of()
				: new LinkedHashSet<>(excludeProductIds);
		Set<String> excludeGoods = new LinkedHashSet<>();
		for (String id : exclude) {
			excludeGoods.add(id.contains(":") ? id.substring(0, id.indexOf(':')) : id);
		}
		List<ShopProductView> candidates = all.stream()
				.filter(product -> !excludeGoods.contains(product.id()))
				.toList();
		if (candidates.isEmpty()) {
			candidates = all;
		}
		return candidates.stream().limit(limit).toList();
	}

	private static ShopProductView toView(Goods goods, String logoUrl) {
		ArtistCardView card = ArtistCardView.from(goods.getArtist(), logoUrl);
		List<ShopVariantView> variants = goods.getVariants().stream()
				.map(v -> new ShopVariantView(
						v.getId(),
						v.getOptionKey(),
						v.getOptionValue(),
						v.displayLabel(),
						v.getStockQuantity()))
				.toList();
		List<String> labels = goods.getCategories().stream()
				.map(c -> c.getLabel())
				.toList();
		List<String> typeKeys = goods.getCategories().stream()
				.map(c -> c.name().toLowerCase())
				.toList();
		GoodsShopCategory shopCategory = resolveShopCategory(goods);
		return new ShopProductView(
				String.valueOf(goods.getId()),
				goods.getArtist().getId(),
				card.nickname(),
				card.logo(),
				goods.getName(),
				goods.getPrice(),
				shopCategory.getFilterKey(),
				shopCategory.getLabel(),
				goods.isMembershipOnly() || shopCategory.isMembershipOnly(),
				null,
				goods.getThumbnailUrl(),
				goods.getDescription(),
				goods.getOfficialUrl(),
				goods.getTotalStock(),
				goods.getMinPositiveStock(),
				variants,
				labels,
				typeKeys
		);
	}

	private static GoodsShopCategory resolveShopCategory(Goods goods) {
		if (goods.getShopCategory() != null) {
			return goods.getShopCategory();
		}
		return goods.isMembershipOnly() ? GoodsShopCategory.MEMBERSHIP : GoodsShopCategory.MD;
	}

	private static Long parseGoodsId(String productId) {
		if (productId == null || productId.isBlank()) {
			return null;
		}
		String raw = productId.trim();
		int sep = raw.indexOf(':');
		try {
			return Long.parseLong(sep > 0 ? raw.substring(0, sep) : raw);
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
