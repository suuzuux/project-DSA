package megane6.weplanet.controller.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;
import megane6.weplanet.domain.entity.MainBanner;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.BannerType;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.main.MainBannerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 최고관리자 > 통합 대시보드 > 배너 영역 관리.
 * 메인 페이지(/) 상단 배너를 등록/수정/노출 전환/삭제한다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/banners")
public class AdminBannerController {

	private final MainBannerService mainBannerService;
	private final AuthenticatedUserResolver userResolver;
	private final Messages messages;

	@GetMapping
	public String list(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		requireAdmin(principal);
		model.addAttribute("banners", mainBannerService.listAll());
		return "admin/banners";
	}

	@GetMapping("/new")
	public String newForm(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		requireAdmin(principal);
		populateForm(model, null);
		return "admin/banner-form";
	}

	@GetMapping("/{bannerId}/edit")
	public String editForm(@PathVariable Long bannerId,
						   @AuthenticationPrincipal AuthenticatedUser principal,
						   Model model) {
		requireAdmin(principal);
		populateForm(model, mainBannerService.get(bannerId));
		return "admin/banner-form";
	}

	@PostMapping
	public String create(@ModelAttribute MainBannerService.BannerForm form,
						 @RequestParam(required = false) MultipartFile image,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes) {
		User admin = requireAdmin(principal);
		try {
			mainBannerService.save(admin, null, form, image);
			redirectAttributes.addFlashAttribute("msg", messages.get("adminBanner.flash.created"));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/admin/banners/new";
		}
		return "redirect:/admin/banners";
	}

	@PostMapping("/{bannerId}")
	public String update(@PathVariable Long bannerId,
						 @ModelAttribute MainBannerService.BannerForm form,
						 @RequestParam(required = false) MultipartFile image,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes) {
		User admin = requireAdmin(principal);
		try {
			mainBannerService.save(admin, bannerId, form, image);
			redirectAttributes.addFlashAttribute("msg", messages.get("adminBanner.flash.updated"));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/admin/banners/" + bannerId + "/edit";
		}
		return "redirect:/admin/banners";
	}

	// 메인 노출 켜기/끄기
	@PostMapping("/{bannerId}/toggle")
	public String toggle(@PathVariable Long bannerId,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		mainBannerService.toggleActive(bannerId);
		redirectAttributes.addFlashAttribute("msg", messages.get("adminBanner.flash.toggled"));
		return "redirect:/admin/banners";
	}

	@PostMapping("/{bannerId}/delete")
	public String delete(@PathVariable Long bannerId,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		mainBannerService.delete(bannerId);
		redirectAttributes.addFlashAttribute("msg", messages.get("adminBanner.flash.deleted"));
		return "redirect:/admin/banners";
	}

	private void populateForm(Model model, MainBanner banner) {
		model.addAttribute("banner", banner);
		model.addAttribute("bannerTypes", BannerType.values());
		model.addAttribute("artistOptions", mainBannerService.artistOptions());
		model.addAttribute("goodsOptions", mainBannerService.goodsOptions());
		model.addAttribute("titleMax", MainBannerService.TITLE_MAX);
		model.addAttribute("bodyMax", MainBannerService.BODY_MAX);
	}

	private User requireAdmin(AuthenticatedUser principal) {
		User user = userResolver.requireAuthenticated(principal);
		if (user.getRole() != Role.ADMIN) {
			throw new IllegalStateException("error.admin.adminOnly");
		}
		return user;
	}
}
