package megane6.weplanet.service;

import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ShopProductView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ShopService {

	private final UserRepository userRepository;
	private final Map<Long, List<ShopProductView>> catalogByArtist = new LinkedHashMap<>();

	public ShopService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public List<ArtistCardView> getShopArtists() {
		return userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();
	}

	public Optional<ArtistCardView> findArtist(Long artistId) {
		return userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.map(ArtistCardView::from);
	}

	public List<ShopProductView> getProducts(Long artistId) {
		ensureCatalog();
		if (artistId == null) {
			return catalogByArtist.values().stream()
					.flatMap(List::stream)
					.toList();
		}
		return catalogByArtist.getOrDefault(artistId, List.of());
	}

	public Optional<ShopProductView> findProduct(String productId) {
		ensureCatalog();
		return catalogByArtist.values().stream()
				.flatMap(List::stream)
				.filter(product -> product.id().equals(productId))
				.findFirst();
	}

	/** 장바구니 추천 — 담긴 상품 제외, 부족하면 전체에서 채움 */
	public List<ShopProductView> getRecommendedProducts(Collection<String> excludeProductIds, int limit) {
		ensureCatalog();
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

	private void ensureCatalog() {
		if (!catalogByArtist.isEmpty()) {
			return;
		}
		for (User artist : userRepository.findByRole(Role.ARTIST)) {
			catalogByArtist.put(artist.getId(), productsFor(artist));
		}
	}

	private List<ShopProductView> productsFor(User artist) {
		ArtistCardView card = ArtistCardView.from(artist);
		String name = card.nickname();
		List<ShopProductView> products = new ArrayList<>();
		products.add(new ShopProductView(
				"md-" + artist.getId() + "-lightstick",
				artist.getId(),
				name,
				card.logo(),
				"2026 " + name + " OFFICIAL LIGHT STICK Ver.2",
				59000,
				"md",
				"MD · 굿즈",
				false,
				null
		));
		products.add(new ShopProductView(
				"md-" + artist.getId() + "-photocard",
				artist.getId(),
				name,
				card.logo(),
				"멤버십 전용 포토카드 세트 (8종)",
				28000,
				"membership",
				"멤버십 전용",
				true,
				null
		));
		products.add(new ShopProductView(
				"md-" + artist.getId() + "-tshirt",
				artist.getId(),
				name,
				card.logo(),
				"2026 " + name + " TOUR 티셔츠 (Black)",
				45000,
				"md",
				"MD · 굿즈",
				false,
				null
		));
		products.add(new ShopProductView(
				"digital-" + artist.getId() + "-package",
				artist.getId(),
				name,
				card.logo(),
				name + " MAP Vol.1 디지털 패키지",
				12000,
				"digital",
				"디지털",
				false,
				"다운로드"
		));
		return products;
	}
}
