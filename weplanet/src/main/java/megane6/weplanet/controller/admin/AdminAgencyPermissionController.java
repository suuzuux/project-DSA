package megane6.weplanet.controller.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.AgencyStatus;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.admin.AdminAgencyPermissionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/agencies")
@RequiredArgsConstructor
public class AdminAgencyPermissionController {
	
	private final AdminAgencyPermissionService service;
	
	@GetMapping
	public String permission(@RequestParam(required = false) String approval,
							 @RequestParam(required = false) String userStatus,
							 @RequestParam(required = false) String agencyStatus,
							 @RequestParam(required = false) String keyword,
							 @AuthenticationPrincipal AuthenticatedUser principal,
							 Model model) {
		requireAdmin(principal);
		Boolean approvedFilter = parseApproval(approval);
		UserStatus userStatusFilter = parseEnum(UserStatus.class, userStatus);
		AgencyStatus agencyStatusFilter = parseEnum(AgencyStatus.class, agencyStatus);
		
		model.addAttribute("permissions", service.getPermissions(
				approvedFilter, userStatusFilter, agencyStatusFilter, keyword));
		model.addAttribute("stats", service.getStats());
		model.addAttribute("selectedApproval", approvedFilter == null
				? "" : approvedFilter ? "APPROVED" : "PENDING");
		model.addAttribute("selectedUserStatus", userStatusFilter == null
				? "" : userStatusFilter.name());
		model.addAttribute("selectedAgencyStatus", agencyStatusFilter == null
				? "" : agencyStatusFilter.name());
		model.addAttribute("keyword", keyword == null ? "" : keyword);
		
		return "admin/agency-permissions";
	}
	
	@PostMapping("/{userId}/approve")
	public String approve(@PathVariable Long userId,
						  @RequestParam(required = false) String approval,
						  @RequestParam(required = false) String userStatus,
						  @RequestParam(required = false) String agencyStatus,
						  @RequestParam(required = false) String keyword,
						  @AuthenticationPrincipal AuthenticatedUser principal,
						  RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> service.approvePermission(userId, principal.getId()),
				"소속사 권한을 승인했습니다.", redirectAttributes);
		addFilters(approval, userStatus, agencyStatus, keyword, redirectAttributes);
		
		return "redirect:/admin/agencies";
	}
	
	@PostMapping("/{userId}/revoke")
	public String revoke(@PathVariable Long userId,
						 @RequestParam(required = false) String approval,
						 @RequestParam(required = false) String userStatus,
						 @RequestParam(required = false) String agencyStatus,
						 @RequestParam(required = false) String keyword,
						 @AuthenticationPrincipal AuthenticatedUser principal,
						 RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> service.revokePermission(userId, principal.getId()),
				"소속사 권한 승인을 취소했습니다.", redirectAttributes);
		addFilters(approval, userStatus, agencyStatus, keyword, redirectAttributes);
		
		return "redirect:/admin/agencies";
	}
	
	private void handle(
			Runnable action,
			String successMessage,
			RedirectAttributes redirectAttributes
	) {
		try {
			action.run();
			redirectAttributes.addFlashAttribute("msg", successMessage);
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
	}
	
	private void addFilters(
			String approval,
			String userStatus,
			String agencyStatus,
			String keyword,
			RedirectAttributes redirectAttributes
	) {
		addFilter("approval", approval, redirectAttributes);
		addFilter("userStatus", userStatus, redirectAttributes);
		addFilter("agencyStatus", agencyStatus, redirectAttributes);
		addFilter("keyword", keyword, redirectAttributes);
	}
	
	private void addFilter(String name, String value,  RedirectAttributes redirectAttributes) {
		if (value != null && !value.isBlank()) {
			redirectAttributes.addAttribute(name, value.trim());
		}
	}
	
	private Boolean parseApproval(String approval) {
		if (approval == null || approval.isBlank()) {
			return null;
		}
		return switch (approval.trim().toUpperCase()) {
			case "APPROVED" -> true;
			case "PENDING" -> false;
			default -> null;
		};
	}
	
	private void requireAdmin(AuthenticatedUser principal) {
		if (principal == null || !"ROLE_ADMIN".equals(principal.getRoleName())) {
			throw new IllegalStateException("관리자 권한이 필요합니다.");
		}
	}
	
	private <E extends Enum<E>> E parseEnum(
			Class<E> enumType,
			String value
	) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Enum.valueOf(enumType, value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
