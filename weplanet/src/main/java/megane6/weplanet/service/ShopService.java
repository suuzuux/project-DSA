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
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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

	private final UserRepository userRepository;
	private final GoodsRepository goodsRepository;
	private final PortalManagementService portalManagementService;
	private final MembershipService membershipService;
	private final MessageSource messageSource;

	public ShopService(UserRepository userRepository,
					   GoodsRepository goodsRepository,
					   PortalManagementService portalManagementService,
					   MembershipService membershipService,
					   MessageSource messageSource) {
		this.userRepository = userRepository;
		this.goodsRepository = goodsRepository;
		this.portalManagementService = portalManagementService;
		this.membershipService = membershipService;
		this.messageSource = messageSource;
	}

	// 화면 언어에 맞춘 에러 메시지를 뽑아오는 공통 헬퍼
	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	/**
	 * 멤버십 전용 상품은 노출은 하되, 담기/구매는 활성 멤버만 허용.
	 */
	public void requirePurchasable(User buyer, ShopProductView product) {
		if (product == null || !product.membershipOnly()) {
			return;
		}
		User artist = userRepository.findById(product.artistId())
				.orElseThrow(() -> new IllegalArgumentException(msg("shop.error.productNotFound")));
		if (!membershipService.isActiveMember(buyer, artist)) {
			throw new IllegalArgumentException(msg("shop.error.membershipOnly"));
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
				.map(portalManagementService::toArtistCard);	// 로고 + 커뮤니티 영문 주소(homeUrl)
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
		return goods.stream().map(g -> this.toView(g, logos.get(g.getArtist().getId()))).toList();
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
					return this.toView(g, portalManagementService.findLogoImageUrl(g.getArtist()));
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

	private ShopProductView toView(Goods goods, String logoUrl) {
		ArtistCardView card = ArtistCardView.from(goods.getArtist(), logoUrl);
		List<ShopVariantView> variants = goods.getVariants().stream()
				.map(v -> new ShopVariantView(
						v.getId(),
						v.getOptionKey(),
						v.getOptionValue(),
						v.displayLabel(),
						v.getStockQuantity()))
				.toList();
		// GoodsCategoryType.getLabel()은 하드코딩된 한국어라서, 화면 로케일에 맞는
		// 문구는 messageKey로 MessageSource에서 조회한다.
		List<String> labels = goods.getCategories().stream()
				.map(c -> msg(c.getMessageKey()))
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
				msg(shopCategory.getMessageKey()),
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
