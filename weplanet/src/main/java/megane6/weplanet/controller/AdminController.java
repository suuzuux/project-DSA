package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.RoleHomeRedirects;
import megane6.weplanet.service.AdminDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
	
	private final AdminDashboardService ads;
	
	@GetMapping("/login")
	public String login(
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		if (principal == null) {
			return "admin/login";
		}
		
		if (isAdmin(principal)) {
			return "redirect:/admin/dashboard";
		}
		
		return RoleHomeRedirects.redirectFor(principal);
	}
	
	@GetMapping({"", "/"})
	public String home(
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		if (principal == null) {
			return "redirect:/admin/login";
		}
		
		if (!isAdmin(principal)) {
			return RoleHomeRedirects.redirectFor(principal);
		}
		
		return "redirect:/admin/dashboard";
	}
	
	@GetMapping("/dashboard")
	public String dashboard(
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		if (principal == null) {
			return "redirect:/admin/login";
		}
		
		if (!isAdmin(principal)) {
			return RoleHomeRedirects.redirectFor(principal);
		}
		
		model.addAttribute(
				"adminNickname",
				principal.getNickname()
		);
		model.addAttribute(
				"stats",
				ads.getStatus()
		);
		
		return "admin/dashboard";
	}
	
	@PostMapping("/logout")
	public String logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		
		if (session != null) {
			session.invalidate();
		}
		
		SecurityContextHolder.clearContext();
		
		return "redirect:/admin/login?logout";
	}
	
	private boolean isAdmin(AuthenticatedUser principal) {
		return "ROLE_ADMIN".equals(principal.getRoleName());
	}
}