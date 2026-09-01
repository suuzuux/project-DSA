package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.AdminDashboardService;
import megane6.weplanet.service.AdminLoginService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
	private final AdminLoginService als;
	private final AdminDashboardService ads;
	
	// 로그인화면
	@GetMapping("/login")
	public String loginPage(@AuthenticationPrincipal AuthenticatedUser principal) {
		// 이미 관리자로 로그인한 상태면 대시보드
		if (principal != null && principal.getAuthorities().stream()
				.anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
			return "redirect:/admin/dashboard";
		}
		return "admin/login";
	}
	
	/**
	 * 1단계 - 인증번호 발송 (화면 이동 없이 결과만
	 */
	@PostMapping("/login/code")
	@ResponseBody
	public Map<String, Object> sendCode(
			@RequestParam String username,
			@RequestParam String password
	) {
		try {
			AdminLoginService.IssuedResult issued = als.issueCode(username, password);
			return Map.of(
					"success", true,
					"verificationKey", issued.verificationKey(),
					"message", issued.maskedEmail() + " 로 인증번호를 보냈습니다."
			);
		} catch (IllegalArgumentException | IllegalStateException e) {
			return Map.of("success", false, "message", e.getMessage());
		} catch (Exception e) {
			// 메일 발송 실패 등. 상세 내용은 로그에만 남기고 사용자에겐 일반 문구를 준다.
			log.error("[관리자 로그인] 인증번호 발송 실패 (username={})", username, e);
			return Map.of("success", false, "message", "인증번호 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
		}
	}
	
	// 2단계 - 인증번호까지 확인하고 로그인 처리
	@PostMapping("/login")
	public String login(
			@RequestParam String username,
			@RequestParam String password,
			@RequestParam(required = false) String verificationKey,
			@RequestParam(required = false) String code,
			HttpServletRequest request,
			Model model
	) {
		try {
			User admin = als.confirmCode(username, password, verificationKey, code);
			loginAs(admin, request);
			return "redirect:/admin/dashboard";
		} catch (IllegalArgumentException | IllegalStateException e) {
			model.addAttribute("errorMessage", e.getMessage());
			return "admin/login";
		}
	}
	
	// 대시보드
	@GetMapping("/dashboard")
	public String dashboard(
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		model.addAttribute("adminNickname", principal.getNickname());
		model.addAttribute("stats", ads.getStats());
		return "admin/dashboard";
	}
	
	/**
	 * 관리자 로그아웃
	 * 공용 로그아웃은 팬 메인으로 보내지만, 관리자는 관리자 로그인 화면으로 돌아가는 게 자연스러워 따로 둠
	 */
	@PostMapping("/logout")
	public String logout(HttpServletRequest request) {
		// 세션을 통째로 버려서 로그인 흔적을 남기지 않음
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return "redirect:/admin/login?logout";
	}
	
	/**
	 * 인증이 모두 끝난 관리자를 실제 로그인 상태로 만든다
	 * 폼 로그인(/login) 쓰지 않고 직접 처리하는 이유 : 관리자는 인증번호까지 확인해야 로그인이 되는데,
	 * 스프링 기본 폼 로그인은 아이디/비밀번호만 보관하기 때문
	 */
	private void loginAs(User admin, HttpServletRequest request) {
		AuthenticatedUser principal = AuthenticatedUser.builder()
				.id(admin.getId())
				.username(admin.getUsername())
				.password(admin.getPassword())
				.nickname(admin.getNickname())
				.roleName(admin.getRole().authority())
				.enabled(admin.isLoginable())
				.build();
		
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				principal,
				null,
				List.of(new SimpleGrantedAuthority(admin.getRole().authority()))
		);
		
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		
		// 세션에 저장해야 다음 요청에서도 로그인 상태가 유지
		// 세션 고정 공격 방지를 위해 기존 세션을 버리고 새로 만듬
		request.getSession(true).setAttribute(
				HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
				context
		);
	}
}
