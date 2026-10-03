package megane6.weplanet.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.controller.AuthenticatedUserResolver;
import megane6.weplanet.domain.dto.event.HashtagArtistOption;
import megane6.weplanet.domain.dto.event.HashtagEventForm;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.event.HashtagEvent;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.event.HashtagEventAdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 최고관리자 > 이벤트 > 해시태그 총공
 *   GET  /admin/events/hashtag              목록
 *   GET  /admin/events/hashtag/new          등록 폼
 *   POST /admin/events/hashtag              등록
 *   GET  /admin/events/hashtag/{id}/edit    수정 폼 (시작 전만)
 *   POST /admin/events/hashtag/{id}         수정
 *   POST /admin/events/hashtag/{id}/delete  삭제 (시작 전만)
 *   GET  /admin/events/hashtag/artists      참여 아티스트 검색 (JSON)
 */
@Controller
@RequestMapping("/admin/events/hashtag")
@RequiredArgsConstructor
public class AdminHashtagEventController {
	
	private final HashtagEventAdminService service;
	private final AuthenticatedUserResolver userResolver;
	private final megane6.weplanet.i18n.Messages messages;
	
	@GetMapping
	public String list(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		requireAdmin(principal);
		model.addAttribute("events", service.getEvents());
		return "admin/hashtag-events";
	}
	
	@GetMapping("/new")
	public String newForm(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		requireAdmin(principal);
		return showForm(model, null, new HashtagEventForm());
	}
	
	@PostMapping
	public String create(
			@ModelAttribute("form") HashtagEventForm form,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		try {
			service.create(admin, form, request.getRemoteAddr());
		} catch (IllegalArgumentException | IllegalStateException e) {
			// redirect 하면 입력한 값이 전부 날아가므로, 오류 메시지와 함께 폼을 그대로 다시 보여준다
			model.addAttribute("error", messages.resolve(e));
			return showForm(model, null, form);
		}
		
		redirectAttributes.addFlashAttribute("msg", messages.get("adminHashtag.flash.created"));
		return "redirect:/admin/events/hashtag";
	}
	
	@GetMapping("/{eventId}/edit")
	public String editForm(
			@PathVariable Long eventId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model,
			RedirectAttributes redirectAttributes
	) {
		requireAdmin(principal);
		
		try {
			return showForm(model, eventId, service.getEditForm(eventId));
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/admin/events/hashtag";
		}
	}
	
	@PostMapping("/{eventId}")
	public String update(
			@PathVariable Long eventId,
			@ModelAttribute("form") HashtagEventForm form,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		try {
			service.update(eventId, admin, form, request.getRemoteAddr());
		} catch (IllegalArgumentException | IllegalStateException e) {
			model.addAttribute("error", messages.resolve(e));
			return showForm(model, eventId, form);
		}
		
		redirectAttributes.addFlashAttribute("msg", messages.get("adminHashtag.flash.updated"));
		return "redirect:/admin/events/hashtag";
	}
	
	@PostMapping("/{eventId}/delete")
	public String delete(
			@PathVariable Long eventId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		try {
			service.delete(eventId, admin, request.getRemoteAddr());
			redirectAttributes.addFlashAttribute("msg", messages.get("adminHashtag.flash.deleted"));
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
		}
		
		return "redirect:/admin/events/hashtag";
	}
	
	// 모니터링 (진행 중이면 화면이 30초마다 새로고침된다)
	@GetMapping("/{eventId}")
	public String monitor(
			@PathVariable Long eventId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model,
			RedirectAttributes redirectAttributes
	) {
		requireAdmin(principal);
		
		try {
			model.addAttribute("dashboard", service.getDashboard(eventId));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/admin/events/hashtag";
		}
		
		return "admin/hashtag-event-monitor";
	}
	
	@PostMapping("/{eventId}/finalize")
	public String finalizeEvent(
			@PathVariable Long eventId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		try {
			service.finalizeEvent(eventId, admin, request.getRemoteAddr());
			redirectAttributes.addFlashAttribute("msg", messages.get("adminHashtag.flash.finalized"));
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
		}
		
		return "redirect:/admin/events/hashtag/" + eventId;
	}
	
	
	// 이벤트 폼의 아티스트 검색창에서 fetch 로 부른다. @ResponseBody → record 목록이 JSON 배열로 나감
	@GetMapping("/artists")
	@ResponseBody
	public List<HashtagArtistOption> searchArtists(
			@RequestParam(required = false) String keyword,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		requireAdmin(principal);
		return service.searchArtists(keyword);
	}
	
	private String showForm(Model model, Long eventId, HashtagEventForm form) {
		model.addAttribute("eventId", eventId);  // null 이면 등록, 값이 있으면 수정
		model.addAttribute("form", form);
		model.addAttribute("targetRows", service.toTargetRows(form));
		model.addAttribute("minDays", HashtagEvent.MIN_DAYS);
		model.addAttribute("maxDays", HashtagEvent.MAX_DAYS);
		return "admin/hashtag-event-form";
	}
	
	private User requireAdmin(AuthenticatedUser principal) {
		User user = userResolver.requireAuthenticated(principal);
		if (user.getRole() != Role.ADMIN) {
			throw new IllegalStateException("error.admin.adminOnly");
		}
		return user;
	}
}