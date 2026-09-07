package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.AdminUserService;
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
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
	private final AdminUserService aus;
	
	@GetMapping
	public String users(@RequestParam(required = false) String role,
						@RequestParam(required = false) String status,
						@RequestParam(required = false) String provider,
						@RequestParam(required = false) String keyword,
						@AuthenticationPrincipal AuthenticatedUser principal,
						Model model) {
		requireAdmin(principal);
		Role roleFilter = parseEnum(Role.class, role);
		UserStatus statusFilter = parseEnum(UserStatus.class, status);
		AuthProvider providerFilter = parseEnum(AuthProvider.class, provider);
		model.addAttribute("users", aus.getUsers(
				roleFilter,
				statusFilter,
				providerFilter,
				keyword
		));
		model.addAttribute("stats", aus.getStats());
		model.addAttribute("selectedRole", roleFilter == null ? "" : roleFilter.name());
		model.addAttribute("selectedStatus", statusFilter == null ? "" : statusFilter.name());
		model.addAttribute("selectedProvider", providerFilter == null ? "" : providerFilter.name());
		model.addAttribute("keyword", keyword == null ? "" : keyword);
		model.addAttribute("currentAdminId", principal.getId());
		
		return "admin/users";
	}
	
	@PostMapping("/{userId}/suspend")
	public String suspendUser(@PathVariable Long userId,
							  @RequestParam(required = false) String role,
							  @RequestParam(required = false) String status,
							  @RequestParam(required = false) String provider,
							  @RequestParam(required = false) String keyword,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> aus.suspendUser(userId, principal.getId()),
				"회원 계정을 정지했습니다.", redirectAttributes);
		addFilters(role, status, provider, keyword, redirectAttributes);
		
		return "redirect:/admin/users";
	}
	
	@PostMapping("/{userId}/reinstate")
	public String reinstateUser(@PathVariable Long userId,
								@RequestParam(required = false) String role,
								@RequestParam(required = false) String status,
								@RequestParam(required = false) String provider,
								@RequestParam(required = false) String keyword,
								@AuthenticationPrincipal AuthenticatedUser principal,
								RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> aus.reinstateUser(userId, principal.getId()),
				"회원 계정의 정지를 해제했습니다.", redirectAttributes);
		addFilters(role, status, provider, keyword, redirectAttributes);
		
		return "redirect:/admin/users";
	}
		
		private void handle(
				Runnable action,
				String successMessage,
				RedirectAttributes redirectAttributes) {
		try {
			action.run();
			redirectAttributes.addFlashAttribute("msg", successMessage);
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
	}
	
	private void addFilters(
			String role,
			String status,
			String provider,
			String keyword,
			RedirectAttributes redirectAttributes
	) {
		addFilter("role", role, redirectAttributes);
		addFilter("status", status, redirectAttributes);
		addFilter("provider", provider, redirectAttributes);
		addFilter("keyword", keyword, redirectAttributes);
	}
	
	private void addFilter(
			String name,
			String value,
			RedirectAttributes redirectAttributes
	) {
		if (value != null && !value.isBlank()) {
			redirectAttributes.addAttribute(name, value.trim());
		}
	}
	
	private void requireAdmin(AuthenticatedUser principal) {
		if (principal == null || !"ROLE_ADMIN".equals(principal.getRoleName())) {
			throw new IllegalStateException("관리자 권한이 필요합니다.");
		}
	}
	
	private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value) {
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

