package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.SiteNoticeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SiteNoticeController {

	private final SiteNoticeService siteNoticeService;
	private final AuthenticatedUserResolver userResolver;

	@GetMapping("/notices")
	public String publicList(Model model) {
		model.addAttribute("notices", siteNoticeService.listPublished());
		return "notices";
	}

	@GetMapping("/notices/{noticeId}")
	public String publicDetail(@PathVariable Long noticeId, Model model) {
		model.addAttribute("notice", siteNoticeService.getPublished(noticeId));
		return "notice-detail";
	}

	@GetMapping("/admin/notices")
	public String adminList(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		requireAdmin(principal);
		model.addAttribute("notices", siteNoticeService.listAll());
		return "admin/notices";
	}

	@GetMapping("/admin/notices/new")
	public String newForm(@AuthenticationPrincipal AuthenticatedUser principal) {
		requireAdmin(principal);
		return "admin/notice-form";
	}

	@GetMapping("/admin/notices/{noticeId}/edit")
	public String editForm(@PathVariable Long noticeId,
						   @AuthenticationPrincipal AuthenticatedUser principal,
						   Model model) {
		requireAdmin(principal);
		model.addAttribute("notice", siteNoticeService.get(noticeId));
		return "admin/notice-form";
	}

	@PostMapping("/admin/notices")
	public String create(@RequestParam String title,
						 @RequestParam String content,
						 @RequestParam(defaultValue = "false") boolean published,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes) {
		User admin = requireAdmin(principal);
		try {
			siteNoticeService.save(admin, null, title, content, published);
			redirectAttributes.addFlashAttribute("msg", "공지가 등록되었습니다.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/admin/notices/new";
		}
		return "redirect:/admin/notices";
	}

	@PostMapping("/admin/notices/{noticeId}")
	public String update(@PathVariable Long noticeId,
						 @RequestParam String title,
						 @RequestParam String content,
						 @RequestParam(defaultValue = "false") boolean published,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes) {
		User admin = requireAdmin(principal);
		try {
			siteNoticeService.save(admin, noticeId, title, content, published);
			redirectAttributes.addFlashAttribute("msg", "공지가 수정되었습니다.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/admin/notices/" + noticeId + "/edit";
		}
		return "redirect:/admin/notices";
	}

	@PostMapping("/admin/notices/{noticeId}/delete")
	public String delete(@PathVariable Long noticeId,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		siteNoticeService.delete(noticeId);
		redirectAttributes.addFlashAttribute("msg", "공지가 삭제되었습니다.");
		return "redirect:/admin/notices";
	}

	private User requireAdmin(AuthenticatedUser principal) {
		User user = userResolver.requireAuthenticated(principal);
		if (user.getRole() != Role.ADMIN) {
			throw new IllegalStateException("관리자만 접근할 수 있습니다.");
		}
		return user;
	}
}
