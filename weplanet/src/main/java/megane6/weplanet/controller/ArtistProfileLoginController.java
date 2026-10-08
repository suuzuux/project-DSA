package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.security.ArtistProfileLoginSupport;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.UserService;
import megane6.weplanet.service.portal.ArtistProfileLoginService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

// 아티스트 2단계 로그인의 프로필 선택 화면 (세션에 대기 그룹 id 가 있어야 표시).
@Controller
@RequestMapping("/portal/profiles")
@RequiredArgsConstructor
public class ArtistProfileLoginController {
	
	private static final String EXPIRED_REDIRECT = "redirect:/portal/login?role=ARTIST&error=profile";
	
	private final ArtistProfileLoginService profileLoginService;
	private final SocialLoginSessionSupport sessionSupport;
	private final LocaleResolver localeResolver;
	private final UserService userService;
	private final Messages messages;
	
	@GetMapping
	public String profiles(HttpSession session, Model model) {
		Long groupId = ArtistProfileLoginSupport.pendingGroupId(session);
		if (groupId == null) {
			return EXPIRED_REDIRECT;
		}
		
		try {
			model.addAttribute("screen", profileLoginService.loadScreen(groupId));
		} catch (IllegalStateException e) {
			ArtistProfileLoginSupport.clear(session);
			return EXPIRED_REDIRECT;
		}
		
		return "portal/profiles";
	}
	
	// 다른 그룹 계정으로 로그인 - 대기 상태를 지우고 1단계로 돌아간다.
	@PostMapping("/cancel")
	public String cancel(HttpSession session) {
		ArtistProfileLoginSupport.clear(session);
		return "redirect:/portal/login?role=ARTIST";
	}
	
	@PostMapping("/{memberId}")
	public String selectProfile(@PathVariable Long memberId,
								@RequestParam String password,
								@RequestParam(required = false) String confirmPassword,
								HttpServletRequest request,
								HttpServletResponse response,
								HttpSession session,
								RedirectAttributes redirectAttributes) {
		Long groupId = ArtistProfileLoginSupport.pendingGroupId(session);
		if (groupId == null) {
			return EXPIRED_REDIRECT;
		}
		
		User member;
		try {
			member = profileLoginService.authenticate(groupId, memberId, password, confirmPassword);
		} catch (IllegalArgumentException | IllegalStateException e) {
			// 같은 프로필 모달을 에러 문구와 함께 다시 열어준다
			redirectAttributes.addFlashAttribute("profileError", messages.resolve(e));
			redirectAttributes.addFlashAttribute("errorMemberId", memberId);
			return "redirect:/portal/profiles";
		}
		
		ArtistProfileLoginSupport.clear(session);
		
		// 세션 고정 공격을 막기 위해 로그인 직전에 세션 id 를 바꾼다.
		request.changeSessionId();
		sessionSupport.loginAs(member, request, response);
		// 로그인 화면에서 고른 언어가 있으면 유지하고, 없으면 멤버의 선호 언어로 맞춘다.
		if (PreferredLocaleResolver.hasExplicitChoice(request)) {
			Locale chosen = localeResolver.resolveLocale(request);
			userService.updateLanguage(member, PreferredLocaleResolver.toLanguage(chosen));
			localeResolver.setLocale(request, response, chosen);
			PreferredLocaleResolver.clearExplicitChoice(request);
		} else {
			localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(member.getPreferredLanguage()));
		}
		
		return "redirect:/community/" + groupId + "/highlight";
	}
}