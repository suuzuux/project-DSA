package megane6.weplanet.service;

import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ShopProductView;
import megane6.weplanet.domain.entity.Goods;
import megane6.weplanet.domain.entity.User;
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

	private final UserRepository userRepository;
	private final GoodsRepository goodsRepository;
	private final PortalManagementService portalManagementService;

	public ShopService(UserRepository userRepository,
					   GoodsRepository goodsRepository,
					   PortalManagementService portalManagementService) {
		this.userRepository = userRepository;
		this.goodsRepository = goodsRepository;
		this.portalManagementService = portalManagementService;
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
		Map<Long, String> logos = portalManagementService.logoImageUrlsByArtistIds(
				goods.stream().map(g -> g.getArtist().getId()).distinct().toList());
		return goods.stream().map(g -> toView(g, logos.get(g.getArtist().getId()))).toList();
	}

	public Optional<ShopProductView> findProduct(String productId) {
		Long id = parseId(productId);
		if (id == null) {
			return Optional.empty();
		}
		return goodsRepository.findByIdAndDeletedAtIsNull(id)
				.filter(g -> g.getStatus() == GoodsStatus.ON_SALE)
				.map(g -> toView(g, portalManagementService.findLogoImageUrl(g.getArtist())));
	}

	public List<ShopProductView> getRecommendedProducts(Collection<String> excludeProductIds, int limit) {
		List<ShopProductView> all = getProducts(null);
		Set<String> exclude = excludeProductIds == null
				? Set.of()
				: new LinkedHashSet<>(excludeProductIds);
		List<ShopProductView> candidates = all.stream()
				.filter(product -> !exclude.contains(product.id()))
				.toList();
		if (candidates.isEmpty()) {
			candidates = all;
		}
		return candidates.stream().limit(limit).toList();
	}

	private static ShopProductView toView(Goods goods, String logoUrl) {
		ArtistCardView card = ArtistCardView.from(goods.getArtist(), logoUrl);
		return new ShopProductView(
				String.valueOf(goods.getId()),
				goods.getArtist().getId(),
				card.nickname(),
				card.logo(),
				goods.getName(),
				goods.getPrice(),
				"md",
				"MD · 굿즈",
				false,
				null,
				goods.getThumbnailUrl(),
				goods.getDescription(),
				goods.getOfficialUrl()
		);
	}

	private static Long parseId(String productId) {
		if (productId == null || productId.isBlank()) {
			return null;
		}
		try {
			return Long.parseLong(productId.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
