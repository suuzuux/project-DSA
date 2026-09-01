package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.BadgeCollectionView;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.CollectionService;
import megane6.weplanet.service.ProjectContributionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class CollectionController {
	/**
	 * 나의 컬렉션(배지) 화면
	 * 로그인한 본인의 배지만 보는 화면이라 url > fanId 받지 X
	 * 항상 고르인 정보(principal)에서 꺼내 씀
	 */
	private final CollectionService cs;
	private final ProjectContributionService pcs;
	
	@GetMapping("/collection")
	public String collection(
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		if (principal == null) {
			return "redirect:/login";
		}
		model.addAttribute("cards", cs.getMyCollection(principal.getId()));
		model.addAttribute(
				"projectParticipations",
				pcs.getMyParticipationHistory(principal.getId())
		);
		
		return "collection";
	}
	
	/**
	 * 전체보기 모달 내용. 화면 전체를 새로 그리지 않고 이 부분만 받아간다.
	 * JS가 fetch로 받아서 모달을 채움
	 */
	@GetMapping("/collection/{artistId}")
	@ResponseBody
	public BadgeCollectionView badgeCollection(
			@PathVariable Long artistId,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		if (principal == null) {
			throw new IllegalStateException("로그인이 필요합니다.");
		}
		return cs.getBadgeCollection(principal.getId(), artistId);
	}
}
