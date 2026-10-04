package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.LoginAttemptService;
import megane6.weplanet.security.RoleHomeRedirects;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.AdminDashboardService;
import megane6.weplanet.service.AdminLoginService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
	
	private final AdminDashboardService ads;
	private final AdminLoginService adminLoginService;
	private final SocialLoginSessionSupport loginSessionSupport;
	private final LoginAttemptService loginAttemptService;
	private final UserRepository userRepository;
	private final Messages messages;
	
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

	/** 아이디/비밀번호를 확인한 뒤 관리자 계정 이메일로 2차 인증번호를 보낸다. */
	@PostMapping("/login/code")
	@ResponseBody
	public Map<String, Object> sendLoginCode(
			@RequestParam String username,
			@RequestParam String password,
			HttpServletRequest request
	) {
		if (isLoginBlocked(username, request)) {
			return failed(messages.get("login.tooManyAttempts", LoginAttemptService.LOCK_MINUTES));
		}

		try {
			AdminLoginService.IssuedResult issued =
					adminLoginService.issueCode(username, password);
			loginAttemptService.recordSuccess(username);
			return Map.of(
					"success", true,
					"verificationKey", issued.verificationKey(),
					"expiresAt", issued.expiresAt().toString()
			);
		} catch (IllegalArgumentException | IllegalStateException e) {
			return failed(resolveLoginFailure(username, request, e));
		} catch (Exception e) {
			// 메일 발송 실패 등의 내부 정보와 관리자 아이디는 응답에 포함하지 않는다.
			log.error("[관리자 로그인] 인증번호 발송 실패", e);
			return failed(messages.get("admin.login.otp.sendFailed"));
		}
	}

	/** 입력한 인증번호를 미리 확인한다. 실제 로그인은 아직 수행하지 않는다. */
	@PostMapping("/login/verify")
	@ResponseBody
	public Map<String, Object> verifyLoginCode(
			@RequestParam String username,
			@RequestParam String password,
			@RequestParam(required = false) String verificationKey,
			@RequestParam(required = false) String code,
			HttpServletRequest request
	) {
		if (isLoginBlocked(username, request)) {
			return failed(messages.get("login.tooManyAttempts", LoginAttemptService.LOCK_MINUTES));
		}

		try {
			adminLoginService.verifyOnly(username, password, verificationKey, code);
			return Map.of("success", true);
		} catch (IllegalArgumentException | IllegalStateException e) {
			return failed(resolveLoginFailure(username, request, e));
		}
	}

	/** 아이디/비밀번호와 이메일 인증을 모두 확인한 뒤 관리자 세션을 만든다. */
	@PostMapping("/login")
	public String completeLogin(
			@RequestParam String username,
			@RequestParam String password,
			@RequestParam(required = false) String verificationKey,
			@RequestParam(required = false) String code,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model
	) {
		model.addAttribute("loginUsername", username);
		if (isLoginBlocked(username, request)) {
			model.addAttribute(
					"errorMessage",
					messages.get("login.tooManyAttempts", LoginAttemptService.LOCK_MINUTES)
			);
			return "admin/login";
		}

		try {
			User admin = adminLoginService.confirmCode(
					username,
					password,
					verificationKey,
					code
			);
			loginSessionSupport.loginAs(admin, request, response);
			loginAttemptService.recordSuccess(username);
			return "redirect:/admin/dashboard";
		} catch (IllegalArgumentException | IllegalStateException e) {
			model.addAttribute("errorMessage", resolveLoginFailure(username, request, e));
			return "admin/login";
		}
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

	private boolean isLoginBlocked(String username, HttpServletRequest request) {
		return loginAttemptService.isBlocked(username, request.getRemoteAddr());
	}

	private String resolveLoginFailure(
			String username,
			HttpServletRequest request,
			RuntimeException exception
	) {
		if ("admin.login.error.badCredentials".equals(exception.getMessage())) {
			boolean accountExists = username != null
					&& !username.isBlank()
					&& userRepository.existsByUsername(username.trim());
			loginAttemptService.recordFailure(
					accountExists ? username : null,
					request.getRemoteAddr()
			);
			if (isLoginBlocked(username, request)) {
				return messages.get("login.tooManyAttempts", LoginAttemptService.LOCK_MINUTES);
			}
		}
		return messages.resolve(exception);
	}

	private static Map<String, Object> failed(String message) {
		return Map.of("success", false, "message", message);
	}
}
