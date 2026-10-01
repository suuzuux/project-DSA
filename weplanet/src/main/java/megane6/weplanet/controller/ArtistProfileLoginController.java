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

/*
	아티스트 2단계 로그인 - 프로필 선택 화면 (와이어프레임 51쪽)
	아직 로그인되지 않은 상태로 들어오므로 SecurityConfig 에서 공개 URL 로 열어두고,
	대신 세션의 "대기 그룹 id"(ArtistProfileLoginSupport)가 있어야만 화면을 보여준다.
 */
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
	
	// "다른 그룹 계정으로 로그인" - 대기 상태를 지우고 1단계로
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
		
		// 로그인 직전에 세션 id 를 바꾼다 (세션 고정 공격 방지 - 폼 로그인은 Spring Security 가 해주지만 여기선 직접)
		request.changeSessionId();
		sessionSupport.loginAs(member, request, response);
		// SETTINGS-03 로케일 버그#2 유형 수정: 프로필 선택도 로그인을 새로 여는 지점이라 세션 로케일을 다시 맞춘다.
		// 포털 로그인 화면에서 언어를 골랐으면(LoginSuccessHandler 가 넘겨준 표시) 그 언어를 유지하고 멤버 계정에 저장,
		// 아니면 이 멤버의 선호 언어로 보여준다.
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