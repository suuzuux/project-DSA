package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.domain.entity.enumfolder.ReportStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.AdminReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 관리자 통합 신고·제재 - 게시글·댓글 신고 기각, 대상 삭제, 작성자 제재. */
@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

	private final AdminReportService adminReportService;
	private final AuthenticatedUserResolver userResolver;
	// 서비스 예외(메시지 키)를 화면 문구로 번역한다.
	private final megane6.weplanet.i18n.Messages messages;

	@GetMapping
	public String list(
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String reason,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		requireAdmin(principal);
		
		AdminReportService.TargetType typeFilter = (type == null || type.isBlank())
				? null : AdminReportService.TargetType.valueOf(type);
		ReportStatus statusFilter = (status == null || status.isBlank())
				? ReportStatus.PENDING : ReportStatus.valueOf(status);
		ReportReason reasonFilter = (reason == null || reason.isBlank())
				? null : ReportReason.valueOf(reason);
		
		model.addAttribute("pageResult", adminReportService.listAll
				(typeFilter, statusFilter, reasonFilter, keyword, page, size));
		model.addAttribute("selectedType", type);
		model.addAttribute("selectedStatus", statusFilter.name());
		model.addAttribute("selectedReason", reason);
		model.addAttribute("keyword", keyword);
		
		return "admin/reports";
	}
	
	@PostMapping("/posts/{postId}/dismiss")
	public String dismissPostReport(
			@PathVariable Long postId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		handle(
				() -> adminReportService.dismissPostReports(
						postId,
						admin.getId(),
						request.getRemoteAddr()
				),
				messages.get("admin.reports.flash.dismissed"),
				redirectAttributes
		);
		
		return "redirect:/admin/reports";
	}
	
	@PostMapping("/posts/{postId}/delete-content")
	public String deleteReportedPost(
			@PathVariable Long postId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		handle(
				() -> adminReportService.deleteReportedPost(
						postId,
						admin,
						request.getRemoteAddr()
				),
				messages.get("admin.reports.flash.postDeleted"),
				redirectAttributes
		);
		
		return "redirect:/admin/reports";
	}
	
	@PostMapping("/comments/{commentId}/dismiss")
	public String dismissCommentReport(
			@PathVariable Long commentId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		handle(
				() -> adminReportService.dismissCommentReports(
						commentId,
						admin.getId(),
						request.getRemoteAddr()
				),
				messages.get("admin.reports.flash.dismissed"),
				redirectAttributes
		);
		
		return "redirect:/admin/reports";
	}
	
	@PostMapping("/comments/{commentId}/delete-content")
	public String deleteReportedComment(
			@PathVariable Long commentId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		handle(
				() -> adminReportService.deleteReportedComment(
						commentId,
						admin,
						request.getRemoteAddr()
				),
				messages.get("admin.reports.flash.commentDeleted"),
				redirectAttributes
		);
		
		return "redirect:/admin/reports";
	}
	
	@PostMapping("/users/{userId}/suspend")
	public String suspendUser(
			@PathVariable Long userId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		handle(
				() -> adminReportService.suspendUser(
						userId,
						admin.getId(),
						request.getRemoteAddr()
				),
				messages.get("admin.reports.flash.userSuspended"),
				redirectAttributes
		);
		
		return "redirect:/admin/reports";
	}

	// 성공·실패 메시지 처리 공통 메서드.
	private void handle(Runnable action, String successMessage, RedirectAttributes redirectAttributes) {
		try {
			action.run();
			redirectAttributes.addFlashAttribute("msg", successMessage);
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
		}
	}

	private User requireAdmin(AuthenticatedUser principal) {
		User user = userResolver.requireAuthenticated(principal);
		if (user.getRole() != Role.ADMIN) {
			throw new IllegalStateException("error.admin.adminOnly");
		}
		return user;
	}
	
	@GetMapping("/suspended")
	public String suspendedUsers(
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		requireAdmin(principal);
		model.addAttribute("suspendedUsers", adminReportService.listSuspendedUsers());
		return "admin/suspended-users";
	}
	
	@PostMapping("/users/{userId}/reinstate")
	public String reinstateUser(
			@PathVariable Long userId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		User admin = requireAdmin(principal);
		
		handle(
				() -> adminReportService.reinstateUser(
						userId,
						admin.getId(),
						request.getRemoteAddr()
				),
				messages.get("admin.reports.flash.reinstated"),
				redirectAttributes
		);
		
		return "redirect:/admin/reports/suspended";
	}
}
