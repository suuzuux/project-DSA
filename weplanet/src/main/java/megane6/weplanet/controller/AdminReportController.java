package megane6.weplanet.controller;

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

/**
 * 관리자 "통합 신고 · 제재" 화면.
 * 게시글 신고 + 댓글 신고를 한 목록에서 보고, 기각/대상 삭제/작성자 제재를 처리한다.
 */
@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

	private final AdminReportService adminReportService;
	private final AuthenticatedUserResolver userResolver;

	@GetMapping
	public String list(
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String reason,
			@RequestParam(required = false) String keyword,
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
		
		model.addAttribute("reports", adminReportService
				.listAll(typeFilter, statusFilter, reasonFilter, keyword));
		model.addAttribute("selectedType", type);
		model.addAttribute("selectedStatus", statusFilter.name());
		model.addAttribute("selectedReason", reason);
		model.addAttribute("keyword", keyword);
		
		return "admin/reports";
	}
	
	@PostMapping("/posts/{postId}/dismiss")
	public String dismissPostReport(@PathVariable Long postId,
									@AuthenticationPrincipal AuthenticatedUser principal,
									RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> adminReportService.dismissPostReports(postId), "신고를 기각했습니다.", redirectAttributes);
		return "redirect:/admin/reports";
	}
	
	@PostMapping("/posts/{postId}/delete-content")
	public String deleteReportedPost(@PathVariable Long postId,
									 @AuthenticationPrincipal AuthenticatedUser principal,
									 RedirectAttributes redirectAttributes) {
		User admin = requireAdmin(principal);
		handle(() -> adminReportService.deleteReportedPost(postId, admin), "신고된 게시글을 삭제했습니다.", redirectAttributes);
		return "redirect:/admin/reports";
	}
	
	@PostMapping("/comments/{commentId}/dismiss")
	public String dismissCommentReport(@PathVariable Long commentId,
									   @AuthenticationPrincipal AuthenticatedUser principal,
									   RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> adminReportService.dismissCommentReports(commentId), "신고를 기각했습니다.", redirectAttributes);
		return "redirect:/admin/reports";
	}
	
	@PostMapping("/comments/{commentId}/delete-content")
	public String deleteReportedComment(@PathVariable Long commentId,
										@AuthenticationPrincipal AuthenticatedUser principal,
										RedirectAttributes redirectAttributes) {
		User admin = requireAdmin(principal);
		handle(() -> adminReportService.deleteReportedComment(commentId, admin), "신고된 댓글을 삭제했습니다.", redirectAttributes);
		return "redirect:/admin/reports";
	}

	@PostMapping("/users/{userId}/suspend")
	public String suspendUser(@PathVariable Long userId,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> adminReportService.suspendUser(userId), "해당 회원을 정지 처리했습니다.", redirectAttributes);
		return "redirect:/admin/reports";
	}

	// 성공/실패 메시지를 매번 똑같이 반복하지 않기 위한 공통 처리
	private void handle(Runnable action, String successMessage, RedirectAttributes redirectAttributes) {
		try {
			action.run();
			redirectAttributes.addFlashAttribute("msg", successMessage);
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
	}

	private User requireAdmin(AuthenticatedUser principal) {
		User user = userResolver.requireAuthenticated(principal);
		if (user.getRole() != Role.ADMIN) {
			throw new IllegalStateException("관리자만 접근할 수 있습니다.");
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
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		requireAdmin(principal);
		handle(() -> adminReportService.reinstateUser(userId), "정지를 해제했습니다.",
				redirectAttributes);
		
		return "redirect:/admin/reports/suspended";
	}
}
