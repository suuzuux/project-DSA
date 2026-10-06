package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.event.HashtagEventPageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 해시태그 총공 이벤트 페이지 (로그인 회원만 - 비회원은 SecurityConfig 규칙에 따라 /login 으로 이동)
 *   GET /events/hashtag            홈 배너 → 지금 대표 이벤트
 *   GET /events/hashtag/{id}       결과 공지 링크 → 그 회차 이벤트
 *   ?all=true                      전체 순위 펼치기 (주소에 남아서 30초 새로고침 후에도 펼친 상태 유지)
 */
@Controller
@RequestMapping("/events/hashtag")
@RequiredArgsConstructor
public class HashtagEventPageController {
	
	private final HashtagEventPageService service;
	
	@GetMapping
	public String featured(@RequestParam(defaultValue = "false") boolean all,
						   @AuthenticationPrincipal AuthenticatedUser principal,
						   Model model) {
		model.addAttribute("view", service.getFeatured(viewerId(principal)).orElse(null));
		model.addAttribute("pagePath", "/events/hashtag");
		model.addAttribute("showAll", all);
		
		return "events/hashtag";
	}
	
	@GetMapping("/{eventId}")
	public String event(@PathVariable Long eventId,
						@RequestParam(defaultValue = "false") boolean all,
						@AuthenticationPrincipal AuthenticatedUser principal,
						Model model) {
		try {
			model.addAttribute("view", service.getEvent(eventId, viewerId(principal)));
		} catch (IllegalArgumentException e) {
			return "redirect:/events/hashtag";
		}
		
		model.addAttribute("pagePath", "/events/hashtag/" + eventId);
		model.addAttribute("showAll", all);
		
		return "events/hashtag";
	}
	
	// 비로그인이면 null → "내 커뮤니티" 표시 없이 보여준다
	private Long viewerId(AuthenticatedUser principal) {
		return principal == null ? null : principal.getId();
	}
}
