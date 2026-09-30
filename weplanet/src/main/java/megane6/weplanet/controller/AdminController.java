package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.RoleHomeRedirects;
import megane6.weplanet.service.AdminDashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
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
	
	// FIX-01: 예전에는 세션만 무효화하고 브라우저의 세션 쿠키(JSESSIONID)는 그대로 둬서, 다음 요청(/admin/login)이
	// "만료된 세션"으로 판단돼 SecurityConfig 의 invalidSessionUrl(/login?expired=true) - 팬 로그인 화면으로 튕겼다.
	// 스프링 기본 로그아웃(/logout)과 같은 처리(세션 무효화 + 인증 정보 삭제 + 세션 쿠키 삭제)를 해서
	// 관리자 로그인 화면(/admin/login?logout)으로 정상 이동하게 한다.
	@PostMapping("/logout")
	public String logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		new SecurityContextLogoutHandler().logout(request, response, authentication);
		new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
		return "redirect:/admin/login?logout";
	}
	
	private boolean isAdmin(AuthenticatedUser principal) {
		return "ROLE_ADMIN".equals(principal.getRoleName());
	}
}