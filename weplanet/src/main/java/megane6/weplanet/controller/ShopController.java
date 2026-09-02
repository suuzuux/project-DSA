package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ShopProductView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.ShopService;
import megane6.weplanet.service.community.CommunityJoinService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class ShopController {

	private final ShopService shopService;
	private final UserRepository userRepository;
	private final AuthenticatedUserResolver userResolver;
	private final CommunityJoinService communityJoinService;

	/** 메인 메뉴 → 전체 굿즈샵 (아티스트 필터 선택 가능) */
	@GetMapping("/shop")
	public String globalShop(@RequestParam(required = false) Long artistId,
	                         @AuthenticationPrincipal AuthenticatedUser principal,
	                         Model model) {
		populateShellMenu(principal, model);
		model.addAttribute("communityMode", false);
		model.addAttribute("artists", shopService.getShopArtists());
		model.addAttribute("selectedArtistId", artistId);
		model.addAttribute("selectedArtist", artistId != null ? shopService.findArtist(artistId).orElse(null) : null);
		model.addAttribute("products", shopService.getProducts(artistId));
		return "shop";
	}

	/** 커뮤니티 헤더 → 해당 아티스트 굿즈만 */
	@GetMapping("/shop/community/{artistId}")
	public String communityShop(@PathVariable Long artistId,
	                            @AuthenticationPrincipal AuthenticatedUser principal,
	                            Model model) {
		ArtistCardView artist = shopService.findArtist(artistId)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
		populateShellMenu(principal, model);
		model.addAttribute("communityMode", true);
		model.addAttribute("artists", List.of(artist));
		model.addAttribute("selectedArtistId", artistId);
		model.addAttribute("selectedArtist", artist);
		model.addAttribute("products", shopService.getProducts(artistId));
		return "shop";
	}

	@GetMapping("/shop/products/{productId}")
	public String productDetail(@PathVariable String productId,
	                            @RequestParam(required = false, defaultValue = "global") String from,
	                            @AuthenticationPrincipal AuthenticatedUser principal,
	                            Model model) {
		ShopProductView product = shopService.findProduct(productId)
				.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
		populateShellMenu(principal, model);
		model.addAttribute("product", product);
		model.addAttribute("fromCommunity", "community".equals(from));
		return "shop-detail";
	}

	private void populateShellMenu(AuthenticatedUser principal, Model model) {
		List<User> artistUsers = userRepository.findByRole(Role.ARTIST);
		List<ArtistCardView> allArtists = artistUsers.stream()
				.map(ArtistCardView::from)
				.toList();
		if (principal == null) {
			model.addAttribute("joinedArtists", Collections.emptyList());
			return;
		}
		User me = userResolver.resolve(principal, 1L);
		Set<Long> joinedArtistIds = communityJoinService.joinedArtistIds(me);
		List<ArtistCardView> joinedArtists = allArtists.stream()
				.filter(a -> joinedArtistIds.contains(a.id()))
				.toList();
		model.addAttribute("joinedArtists", joinedArtists);
	}
}
