package megane6.weplanet.controller.notice;
import megane6.weplanet.service.main.ContentTranslationService;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.event.HashtagResultNoticeDraft;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.NoticeCategory;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.notice.SiteNoticeService;
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
	// 서비스 예외(메시지 키)를 현재 로케일 문구로 바꾼다.
	private final megane6.weplanet.i18n.Messages messages;
	private final AuthenticatedUserResolver userResolver;
	
	private final ShopImageStorage shopImageStorage; // 에디터 이미지 저장 (굿즈 에디터와 같은 검사)
	
	private final HashtagEventAdminService hashtagEventAdminService; // [해시태그 총공] 결과 공지 초안
	private final megane6.weplanet.service.main.ContentTranslationService contentTranslationService; // 공지 번역보기 (AI)

	// 공지사항 목록 한 페이지에 보여줄 개수
	private static final int NOTICE_PAGE_SIZE = 15;

	@GetMapping("/notices")
	public String publicList(
			@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "1") int page,
			Model model
	) {
		NoticeCategory categoryFilter = (category == null || category.isBlank())
				? null : NoticeCategory.valueOf(category);
		List<megane6.weplanet.domain.entity.SiteNotice> all = siteNoticeService.listPublished(categoryFilter);

		// 15개씩 페이지를 나눈다 (page 는 1부터, 고정 공지는 1페이지).
		int totalPages = Math.max(1, (all.size() + NOTICE_PAGE_SIZE - 1) / NOTICE_PAGE_SIZE);
		int currentPage = Math.min(Math.max(page, 1), totalPages);
		int from = (currentPage - 1) * NOTICE_PAGE_SIZE;
		int to = Math.min(from + NOTICE_PAGE_SIZE, all.size());

		model.addAttribute("notices", all.subList(from, to));
		// 빈 category 도 "전체" 칩이 선택되도록 null 로 맞춘다.
		model.addAttribute("selectedCategory", categoryFilter == null ? null : category);
		model.addAttribute("currentPage", currentPage);
		model.addAttribute("totalPages", totalPages);

		return "common/notice/notices";
	}

	@GetMapping("/notices/{noticeId}")
	public String publicDetail(@PathVariable Long noticeId, Model model) {
		model.addAttribute("notice", siteNoticeService.getPublished(noticeId));
		return "common/notice/notice-detail";
	}

	// 공지 AI 번역 (로그인 사용자의 기본 서비스 언어).
	@PostMapping("/notices/{noticeId}/translate")
	@ResponseBody
	public Map<String, Object> translatePublic(@PathVariable Long noticeId,
											   @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return Map.of("success", false, "message", messages.get("common.error.loginRequired"));
		}
		User user = userResolver.requireAuthenticated(principal);
		var notice = siteNoticeService.getPublished(noticeId);
		return contentTranslationService.noticeResponse(notice.getTitle(), notice.getContent(), user.getPreferredLanguage());
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
		
		// 총공 결과 공지 작성으로 오면 초안을 채운다 (수정 모드가 되지 않게 draft* 로 넘김).
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
	
	// 공지 에디터 이미지 업로드 - base64 대신 서버에 저장하고 URL 을 돌려준다.
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
