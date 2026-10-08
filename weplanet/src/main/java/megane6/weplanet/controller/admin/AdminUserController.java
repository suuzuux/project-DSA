package megane6.weplanet.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.admin.AdminUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
	private final AdminUserService aus;
	private final megane6.weplanet.i18n.Messages messages;
	
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
		// "LOCAL"(연동된 소셜이 없는 계정)은 AuthProvider 값이 아니라 parseEnum 하면 필터가 풀려 버리므로,
		// provider IS NULL 조건(localOnly)으로 따로 처리한다.
		boolean localOnly = "LOCAL".equalsIgnoreCase(provider);
		AuthProvider providerFilter = localOnly ? null : parseEnum(AuthProvider.class, provider);
		model.addAttribute("users", aus.getUsers(
				roleFilter,
				statusFilter,
				providerFilter,
				localOnly,
				keyword
		));
		model.addAttribute("stats", aus.getStats());
		model.addAttribute("selectedRole", roleFilter == null ? "" : roleFilter.name());
		model.addAttribute("selectedStatus", statusFilter == null ? "" : statusFilter.name());
		model.addAttribute("selectedProvider", localOnly ? "LOCAL" : (providerFilter == null ? "" : providerFilter.name()));
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
							  HttpServletRequest request,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> aus.suspendUser(userId, principal.getId(), request.getRemoteAddr()),
				messages.get("admin.users.flash.suspended"), redirectAttributes);
		addFilters(role, status, provider, keyword, redirectAttributes);
		
		return "redirect:/admin/users";
	}
	
	@PostMapping("/{userId}/reinstate")
	public String reinstateUser(@PathVariable Long userId,
								@RequestParam(required = false) String role,
								@RequestParam(required = false) String status,
								@RequestParam(required = false) String provider,
								@RequestParam(required = false) String keyword,
								HttpServletRequest request,
								@AuthenticationPrincipal AuthenticatedUser principal,
								RedirectAttributes redirectAttributes) {
		requireAdmin(principal);
		handle(() -> aus.reinstateUser(userId, principal.getId(), request.getRemoteAddr()),
				messages.get("admin.users.flash.reinstated"), redirectAttributes);
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
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
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
			throw new IllegalStateException("error.admin.adminOnly");
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

