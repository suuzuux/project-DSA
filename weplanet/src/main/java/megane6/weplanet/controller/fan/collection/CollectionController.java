package megane6.weplanet.controller.fan.collection;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.BadgeCollectionView;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.fan.BadgePeriodService;
import megane6.weplanet.service.fan.CollectionService;
import megane6.weplanet.service.project.ProjectContributionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class CollectionController {
	/** 로그인한 본인의 배지 컬렉션 화면 (fanId 를 URL 로 받지 않음). */
	private final CollectionService cs;
	private final ProjectContributionService pcs;
	private final BadgePeriodService bps;
	
	@GetMapping("/collection")
	public String collection(
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		if (principal == null) {
			return "redirect:/login";
		}
		// 아티스트·멤버 계정은 배지를 받지 않아 컬렉션을 쓰지 않는다.
		if (isArtistAccount(principal)) {
			return "redirect:/";
		}
		// 화면 진입 시 본인 기간 배지를 한 번 더 확인해 바로 보이게 한다.
		bps.checkForFan(principal.getId());
		
		model.addAttribute("cards", cs.getMyCollection(principal.getId()));
		model.addAttribute(
				"projectParticipations",
				pcs.getMyParticipationHistory(principal.getId())
		);
		
		return "fan/collection/collection";
	}
	
	/** 전체보기 모달 내용만 부분 렌더링한다 (JS 가 fetch). */
	@GetMapping("/collection/{artistId}")
	@ResponseBody
	public BadgeCollectionView badgeCollection(
			@PathVariable Long artistId,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		if (principal == null) {
			throw new IllegalStateException("common.error.loginRequired");
		}
		if (isArtistAccount(principal)) {
			throw new AccessDeniedException("error.forbidden");
		}
		return cs.getBadgeCollection(principal.getId(), artistId);
	}

	private boolean isArtistAccount(AuthenticatedUser principal) {
		String role = principal.getRoleName();
		return "ROLE_ARTIST".equals(role) || "ROLE_ARTIST_MEMBER".equals(role);
	}
}
