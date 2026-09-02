package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.AdminReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
	public String list(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		requireAdmin(principal);
		model.addAttribute("reports", adminReportService.listAll());
		return "admin/reports";
	}

	@PostMapping("/posts/{reportId}/dismiss")
	public String dismissPostReport(@PathVariable Long reportId,
									 @AuthenticationPrincipal AuthenticatedUser principal,
									 RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> adminReportService.dismissPostReport(reportId), "신고를 기각했습니다.", redirectAttributes);
		return "redirect:/admin/reports";
	}

	@PostMapping("/posts/{reportId}/delete-content")
	public String deleteReportedPost(@PathVariable Long reportId,
									  @AuthenticationPrincipal AuthenticatedUser principal,
									  RedirectAttributes redirectAttributes) {
		User admin = requireAdmin(principal);
		handle(() -> adminReportService.deleteReportedPost(reportId, admin), "신고된 게시글을 삭제했습니다.", redirectAttributes);
		return "redirect:/admin/reports";
	}

	@PostMapping("/comments/{reportId}/dismiss")
	public String dismissCommentReport(@PathVariable Long reportId,
										@AuthenticationPrincipal AuthenticatedUser principal,
										RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> adminReportService.dismissCommentReport(reportId), "신고를 기각했습니다.", redirectAttributes);
		return "redirect:/admin/reports";
	}

	@PostMapping("/comments/{reportId}/delete-content")
	public String deleteReportedComment(@PathVariable Long reportId,
										 @AuthenticationPrincipal AuthenticatedUser principal,
										 RedirectAttributes redirectAttributes) {
		User admin = requireAdmin(principal);
		handle(() -> adminReportService.deleteReportedComment(reportId, admin), "신고된 댓글을 삭제했습니다.", redirectAttributes);
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
}
