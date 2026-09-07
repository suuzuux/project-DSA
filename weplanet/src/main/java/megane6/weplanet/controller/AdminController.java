package megane6.weplanet.controller;

import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.RoleHomeRedirects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

	@GetMapping("/login")
	public String login(@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return "admin/login";
		}
		if (isAdmin(principal)) {
			return "redirect:/admin";
		}
		return RoleHomeRedirects.redirectFor(principal);
	}

	@GetMapping({"", "/"})
	public String home(@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return "redirect:/admin/login";
		}
		if (!isAdmin(principal)) {
			return RoleHomeRedirects.redirectFor(principal);
		}
		return "redirect:/admin/notices";
	}

	private boolean isAdmin(AuthenticatedUser principal) {
		return "ROLE_ADMIN".equals(principal.getRoleName());
	}
}
