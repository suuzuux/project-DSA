package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.AdminCommunityService;
import megane6.weplanet.service.ProjectService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/communities")
@RequiredArgsConstructor
public class AdminCommunityController {
	
	private final AdminCommunityService acs;
	private final ProjectService ps;
	
	@GetMapping
	public String dashboard(@RequestParam(required = false) Long projectId,
							Model model) {
		model.addAttribute("stats", acs.getStats());
		model.addAttribute("pendingProjects", acs.getPendingProjects());
		
		if (projectId != null) {
			model.addAttribute("selectedProject", acs.getProjectReviewDetail(projectId));
		}
		
		return "admin/communities";
	}
	
	@PostMapping("/projects/{projectId}/approve")
	public String approveProject(
			@PathVariable Long projectId,
			@RequestParam Long artistId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes) {
		
		requireAdminLogin(principal);
		handle(
				() -> ps.approveProject(
						projectId,
						artistId,
						principal.getId()
				),
				"프로젝트를 승인했습니다.",
				redirectAttributes
		);
		return "redirect:/admin/communities";
	}
	
	@PostMapping("/projects/{projectId}/reject")
	public String rejectProject(@PathVariable Long projectId,
								@RequestParam Long artistId,
								@RequestParam String rejectionReason,
								@AuthenticationPrincipal AuthenticatedUser principal,
								RedirectAttributes redirectAttributes) {
		requireAdminLogin(principal);
		handle(
				() -> ps.rejectProject(
						projectId,
						artistId,
						principal.getId(),
						rejectionReason
				), "프로젝트를 반려했습니다.", redirectAttributes
		);
		return "redirect:/admin/communities";
	}
	
	private void handle(Runnable action, String successMessage,
						RedirectAttributes redirectAttributes) {
		try {
			action.run();
			redirectAttributes.addFlashAttribute("msg", successMessage);
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
	}
	
	private void requireAdminLogin(AuthenticatedUser principal) {
		if (principal == null) {
			throw new IllegalStateException("ADMIN 로그인이 필요합니다.");
		}
	}
}
