package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.AdminCommunityOverviewResponse;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.AdminCommunityService;
import megane6.weplanet.service.ProjectService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/communities")
@RequiredArgsConstructor
public class AdminCommunityController {
	
	private final AdminCommunityService acs;
	private final ProjectService ps;
	
	@GetMapping
	public String dashboard(@RequestParam(required = false) Long projectId,
							@RequestParam(required = false) String status,
							@RequestParam(required = false) String keyword,
							Model model) {
		FanProjectStatus statusFilter = parseStatus(status);
		model.addAttribute("stats", acs.getStats());
		model.addAttribute("pendingProjects", acs.getPendingProjects());
		model.addAttribute("adminProjects", acs.getAdminProjects(statusFilter, keyword));
		model.addAttribute("settlementProjects", acs.getSettlementProjects());
		model.addAttribute("projectStatuses", FanProjectStatus.values());
		model.addAttribute("selectedStatus", statusFilter == null ? "" : statusFilter.name());
		model.addAttribute("keyword", keyword == null ? "" : keyword);
		
		if (projectId != null) {
			model.addAttribute("selectedProject", acs.getProjectReviewDetail(projectId));
		}
		
		return "admin/communities";
	}
	
	@GetMapping("/overview")
	public String communityOverview(@RequestParam(required = false) String keyword, Model model) {
		List<AdminCommunityOverviewResponse> communities = acs.getCommunityOverview(keyword);
		model.addAttribute("communities", communities);
		model.addAttribute("overviewStats", acs.getCommunityOverviewStats(communities));
		model.addAttribute("keyword", keyword == null ? "" : keyword);
		
		return "admin/community-overview";
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
	
	@PostMapping("/settlements/{projectId}/verify")
	public String verifySettlementAccount(@PathVariable Long projectId,
										  @AuthenticationPrincipal AuthenticatedUser principal,
										  RedirectAttributes redirectAttributes) {
		requireAdminLogin(principal);
		handle(() -> acs.verifySettlementAccount(projectId, principal.getId()),
				"정산 계좌를 확인 완료 처리했습니다.",
				redirectAttributes);
		return "redirect:/admin/communities";
	}
	
	@PostMapping("/settlements/{projectId}/fail")
	public String failSettlementAccountVerification(@PathVariable Long projectId,
													@AuthenticationPrincipal AuthenticatedUser principal,
													RedirectAttributes redirectAttributes) {
		requireAdminLogin(principal);
		handle(() -> acs.failSettlementAccountVerification(projectId, principal.getId()),
				"정산 계좌 확인 실패 처리했습니다.",
				redirectAttributes);
		return "redirect:/admin/communities";
	}
	
	@PostMapping("/settlements/{projectId}/complete")
	public String completeSettlement(@PathVariable Long projectId,
									 @AuthenticationPrincipal AuthenticatedUser principal,
									 RedirectAttributes redirectAttributes) {
		requireAdminLogin(principal);
		handle(() -> acs.completeSettlement(projectId, principal.getId()),
				"프로젝트 정산을 완료했습니다.",
				redirectAttributes);
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
	
	private FanProjectStatus parseStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		try {
			return FanProjectStatus.valueOf(status);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
