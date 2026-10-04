package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.event.HashtagResultNoticeDraft;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.NoticeCategory;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.SiteNoticeService;
import megane6.weplanet.service.event.HashtagEventAdminService;
import megane6.weplanet.service.shop.ShopImageStorage;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SiteNoticeController {

	private final SiteNoticeService siteNoticeService;
	// 서비스 예외(메시지 키)를 화면에 내보낼 때 현재 로케일 문구로 해석
	private final megane6.weplanet.i18n.Messages messages;
	private final AuthenticatedUserResolver userResolver;
	
	private final ShopImageStorage shopImageStorage; // 에디터 이미지 저장 (굿즈 에디터와 같은 검사 규칙 재사용)
	
	private final HashtagEventAdminService hashtagEventAdminService; // [해시태그 총공] 결과 공지 초안

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
			@RequestParam(required = false) Long hashtagEventId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		requireAdmin(principal);
		model.addAttribute("pinnedCount", siteNoticeService.countPinned());
		model.addAttribute("maxPinned", SiteNoticeService.MAX_PINNED);
		
		// [해시태그 총공] 모니터링의 "결과 공지 작성" 버튼으로 오면 제목·본문·분류를 미리 채운다
		// (notice 가 아니라 draft* 로 넘기는 이유: notice 가 있으면 폼이 "수정 모드"가 되기 때문)
		if (hashtagEventId != null) {
			try {
				HashtagResultNoticeDraft draft = hashtagEventAdminService.buildResultNotice(hashtagEventId);
				model.addAttribute("draftTitle", draft.title());
				model.addAttribute("draftContent", draft.content());
				model.addAttribute("draftCategory", NoticeCategory.EVENT.name());
			} catch (IllegalArgumentException | IllegalStateException e) {
				model.addAttribute("error", messages.resolve(e));
			}
		}
		
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
	
	// 공지 에디트 (Toast UI) 이미지 업로드.
	// 에디터 기본 동작은 이미지를 base64 글자로 본문에 통째로 넣어버려서, 대신 서버에 파일을 저장하고 ULR만 돌려준다.
	@PostMapping("/admin/notices/editor-image")
	@ResponseBody
	public Map<String, String> editorImage(@RequestParam("image") MultipartFile image,
										   @AuthenticationPrincipal AuthenticatedUser principal) {
		requireAdmin(principal);
		
		try {
			String storedName = shopImageStorage.storeImage(image);
			return Map.of("url", "/uploads/" + storedName);
		} catch (IllegalArgumentException e) {
			return Map.of("message", messages.resolve(e));
		}
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
