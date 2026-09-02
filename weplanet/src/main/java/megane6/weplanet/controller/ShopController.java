package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ShopCartItemView;
import megane6.weplanet.domain.dto.ShopCartSummaryView;
import megane6.weplanet.domain.dto.ShopProductView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.ShopCartService;
import megane6.weplanet.service.ShopService;
import megane6.weplanet.service.community.CommunityJoinService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ShopController {

	private static final String SESSION_SHOP_RETURN = "shopReturnUrl";

	private final ShopService shopService;
	private final ShopCartService shopCartService;
	private final UserRepository userRepository;
	private final AuthenticatedUserResolver userResolver;
	private final CommunityJoinService communityJoinService;

	/** 메인 메뉴 → 전체 굿즈샵 (아티스트 필터 선택 가능) */
	@GetMapping("/shop")
	public String globalShop(@RequestParam(required = false) Long artistId,
	                         @AuthenticationPrincipal AuthenticatedUser principal,
	                         HttpServletRequest request,
	                         Model model) {
		rememberShopPage(request);
		populateShellMenu(principal, model);
		populateCartBadge(principal, model);
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
	                            HttpServletRequest request,
	                            Model model) {
		rememberShopPage(request);
		ArtistCardView artist = shopService.findArtist(artistId)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
		populateShellMenu(principal, model);
		populateCartBadge(principal, model);
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
	                            HttpServletRequest request,
	                            Model model) {
		rememberShopPage(request);
		ShopProductView product = shopService.findProduct(productId)
				.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
		populateShellMenu(principal, model);
		populateCartBadge(principal, model);
		model.addAttribute("product", product);
		model.addAttribute("fromCommunity", "community".equals(from));
		return "shop-detail";
	}

	@GetMapping("/shop/cart")
	public String cart(@AuthenticationPrincipal AuthenticatedUser principal,
	                     HttpSession session,
	                     Model model) {
		if (principal == null) {
			return "redirect:/login";
		}
		User me = userResolver.requireAuthenticated(principal);
		ShopCartSummaryView cart = shopCartService.getCartSummary(me);
		Set<String> inCartProductIds = cart.items().stream()
				.map(item -> item.product().id())
				.collect(Collectors.toCollection(LinkedHashSet::new));
		populateShellMenu(principal, model);
		model.addAttribute("cart", cart);
		model.addAttribute("cartItemCount", cart.items().size());
		model.addAttribute("shopReturnUrl", resolveShopReturnUrl(session));
		model.addAttribute("recommendedProducts", shopService.getRecommendedProducts(inCartProductIds, 12));
		return "shop-cart";
	}

	@PostMapping("/shop/cart/add")
	@ResponseBody
	public Map<String, Object> addToCart(@RequestParam String productId,
	                                     @RequestParam(defaultValue = "1") int quantity,
	                                     @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return Map.of("ok", false, "message", "로그인이 필요합니다.");
		}
		User me = userResolver.requireAuthenticated(principal);
		shopCartService.addItem(me, productId, quantity);
		ShopCartSummaryView cart = shopCartService.getCartSummary(me);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ok", true);
		body.put("message", "장바구니에 담았습니다.");
		body.put("cartCount", shopCartService.countItems(me));
		body.put("addedProductId", productId);
		body.put("cart", toCartJson(cart));
		return body;
	}

	@PostMapping("/shop/cart/{itemId}/update")
	public String updateCartQuantity(@PathVariable Long itemId,
	                                 @RequestParam int quantity,
	                                 @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return "redirect:/login";
		}
		User me = userResolver.requireAuthenticated(principal);
		shopCartService.updateQuantity(me, itemId, quantity);
		return "redirect:/shop/cart";
	}

	@PostMapping("/shop/cart/{itemId}/remove")
	public String removeFromCart(@PathVariable Long itemId,
	                             @AuthenticationPrincipal AuthenticatedUser principal,
	                             RedirectAttributes redirectAttributes) {
		if (principal == null) {
			return "redirect:/login";
		}
		User me = userResolver.requireAuthenticated(principal);
		shopCartService.removeItem(me, itemId);
		redirectAttributes.addFlashAttribute("message", "장바구니에서 삭제했습니다.");
		return "redirect:/shop/cart";
	}

	private void rememberShopPage(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (!path.startsWith("/shop") || path.equals("/shop/cart") || path.startsWith("/shop/cart/")) {
			return;
		}
		String query = request.getQueryString();
		String url = query != null && !query.isBlank() ? path + "?" + query : path;
		request.getSession().setAttribute(SESSION_SHOP_RETURN, url);
	}

	private String resolveShopReturnUrl(HttpSession session) {
		Object stored = session.getAttribute(SESSION_SHOP_RETURN);
		if (stored instanceof String url && !url.isBlank() && !url.startsWith("/shop/cart")) {
			return url;
		}
		return "/shop";
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

	private void populateCartBadge(AuthenticatedUser principal, Model model) {
		if (principal == null) {
			model.addAttribute("cartItemCount", 0);
			return;
		}
		User me = userResolver.resolve(principal, 1L);
		model.addAttribute("cartItemCount", shopCartService.countItems(me));
	}

	private Map<String, Object> toCartJson(ShopCartSummaryView cart) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("items", cart.items().stream().map(this::toCartItemJson).toList());
		map.put("subtotal", cart.subtotal());
		map.put("shippingFee", cart.shippingFee());
		map.put("total", cart.total());
		map.put("formattedSubtotal", cart.formattedSubtotal());
		map.put("formattedShippingFee", cart.formattedShippingFee());
		map.put("formattedTotal", cart.formattedTotal());
		map.put("empty", cart.empty());
		return map;
	}

	private Map<String, Object> toCartItemJson(ShopCartItemView item) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("itemId", item.itemId());
		map.put("quantity", item.quantity());
		map.put("unitPrice", item.unitPrice());
		map.put("lineTotal", item.lineTotal());
		map.put("formattedLineTotal", item.formattedLineTotal());
		map.put("product", toProductJson(item.product()));
		return map;
	}

	private Map<String, Object> toProductJson(ShopProductView product) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", product.id());
		map.put("title", product.title());
		map.put("artistName", product.artistName());
		map.put("categoryLabel", product.categoryLabel());
		map.put("membershipOnly", product.membershipOnly());
		map.put("formattedPrice", product.formattedPrice());
		return map;
	}
}
