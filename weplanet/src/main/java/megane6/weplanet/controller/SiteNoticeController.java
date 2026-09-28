package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.NoticeCategory;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.SiteNoticeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SiteNoticeController {

	private final SiteNoticeService siteNoticeService;
	// SETTINGS-03 커밋5: 서비스 예외(메시지 키)를 화면에 내보낼 때 현재 로케일 문구로 해석
	private final megane6.weplanet.i18n.Messages messages;
	private final AuthenticatedUserResolver userResolver;

	@GetMapping("/notices")
	public String publicList(
			@RequestParam(required = false) String category,
			Model model
	) {
		NoticeCategory categoryFilter = (category == null || category.isBlank())
				? null : NoticeCategory.valueOf(category);
		model.addAttribute("notices", siteNoticeService.listPublished(categoryFilter));
		model.addAttribute("selectedCategory", category);
		
		return "notices";
	}

	@GetMapping("/notices/{noticeId}")
	public String publicDetail(@PathVariable Long noticeId, Model model) {
		model.addAttribute("notice", siteNoticeService.getPublished(noticeId));
		return "notice-detail";
	}

	@GetMapping("/admin/notices")
	public String adminList(
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		requireAdmin(principal);
		
		NoticeCategory categoryFilter = (category == null || category.isBlank())
				? null : NoticeCategory.valueOf(category);
		
		model.addAttribute(
				"pageResult", siteNoticeService.listAll(categoryFilter, keyword, page, size));
		model.addAttribute("stats", siteNoticeService.getStats());
		model.addAttribute("selectedCategory", category);
		model.addAttribute("keyword", keyword);
		
		return "admin/notices";
	}

	@GetMapping("/admin/notices/new")
	public String newForm(
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		requireAdmin(principal);
		model.addAttribute("pinnedCount", siteNoticeService.countPinned());
		model.addAttribute("maxPinned", SiteNoticeService.MAX_PINNED);
		return "admin/notice-form";
	}

	@GetMapping("/admin/notices/{noticeId}/edit")
	public String editForm(
			@PathVariable Long noticeId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		requireAdmin(principal);
		model.addAttribute("notice", siteNoticeService.get(noticeId));
		model.addAttribute("pinnedCount", siteNoticeService.countPinned());
		model.addAttribute("maxPinned", SiteNoticeService.MAX_PINNED);
		return "admin/notice-form";
	}

	@PostMapping("/admin/notices")
	public String create(@RequestParam String title,
						 @RequestParam String content,
						 @RequestParam(defaultValue = "false") boolean published,
						 @RequestParam(required = false) String publishAt,
						 @RequestParam(defaultValue = "false") boolean pinned,
						 @RequestParam NoticeCategory category,
						 HttpServletRequest request,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		try {
			LocalDateTime publishAtValue =
					(publishAt == null || publishAt.isBlank())
						? null : LocalDate.parse(publishAt).atStartOfDay();
			siteNoticeService.save(
					admin,
					null,
					category,
					title,
					content,
					published,
					publishAtValue,
					pinned,
					request.getRemoteAddr());
			redirectAttributes.addFlashAttribute("msg", messages.get("noticeForm.msg.created"));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/admin/notices/new";
		}
		return "redirect:/admin/notices";
	}
	
	@PostMapping("/admin/notices/{noticeId}")
	public String update(@PathVariable Long noticeId,
						 @RequestParam String title,
						 @RequestParam String content,
						 @RequestParam(defaultValue = "false") boolean published,
						 @RequestParam(required = false) String publishAt,
						 @RequestParam(defaultValue = "false") boolean pinned,
						 @RequestParam NoticeCategory category,
						 HttpServletRequest request,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		try {
			LocalDateTime publishAtValue =
					(publishAt == null || publishAt.isBlank())
							? null
							: LocalDate.parse(publishAt).atStartOfDay();
			
			siteNoticeService.save(
					admin,
					noticeId,
					category,
					title,
					content,
					published,
					publishAtValue,
					pinned,
					request.getRemoteAddr()
			);
			
			redirectAttributes.addFlashAttribute(
					"msg",
					messages.get("noticeForm.msg.updated")
			);
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute(
					"error",
					messages.resolve(e)
			);
			return "redirect:/admin/notices/" + noticeId + "/edit";
		}
		
		return "redirect:/admin/notices";
	}
	
	@PostMapping("/admin/notices/{noticeId}/delete")
	public String delete(
			@PathVariable Long noticeId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		siteNoticeService.delete(
				noticeId,
				admin,
				request.getRemoteAddr()
		);
		
		redirectAttributes.addFlashAttribute(
				"msg",
				messages.get("portal.msg.noticeDeleted")
		);
		
		return "redirect:/admin/notices";
	}
	
	@PostMapping("/admin/notices/reorder")
	@ResponseBody
	public Map<String, Object> reorder(
			@RequestParam List<Long> ids,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		requireAdmin(principal);
		try {
			siteNoticeService.reorderPinned(ids);
			return Map.of("ok", true);
		} catch (IllegalArgumentException e) {
			return Map.of("ok", false, "message", messages.resolve(e));
		}
	}

	private User requireAdmin(AuthenticatedUser principal) {
		User user = userResolver.requireAuthenticated(principal);
		if (user.getRole() != Role.ADMIN) {
			throw new IllegalStateException("error.admin.adminOnly");
		}
		return user;
	}
}
