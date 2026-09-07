package megane6.weplanet.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.AgencyStatus;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.admin.AdminArtistService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/artists")
@RequiredArgsConstructor
public class AdminArtistController {
	
	private final AdminArtistService service;
	
	@GetMapping
	public String artists(
			@RequestParam(required = false) String userStatus,
			@RequestParam(required = false) String agencyStatus,
			@RequestParam(required = false) String keyword,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		requireAdmin(principal);
		
		UserStatus userStatusFilter =
				parseEnum(UserStatus.class, userStatus);
		
		AgencyStatus agencyStatusFilter =
				parseEnum(AgencyStatus.class, agencyStatus);
		
		model.addAttribute(
				"artists",
				service.getArtists(
						userStatusFilter,
						agencyStatusFilter,
						keyword
				)
		);
		
		model.addAttribute("stats", service.getStats());
		
		model.addAttribute(
				"selectedUserStatus",
				userStatusFilter == null
						? ""
						: userStatusFilter.name()
		);
		
		model.addAttribute(
				"selectedAgencyStatus",
				agencyStatusFilter == null
						? ""
						: agencyStatusFilter.name()
		);
		
		model.addAttribute(
				"keyword",
				keyword == null ? "" : keyword
		);
		
		return "admin/artists";
	}
	
	@PostMapping("/{userId}/suspend")
	public String suspendArtist(
			@PathVariable Long userId,
			@RequestParam(required = false) String userStatus,
			@RequestParam(required = false) String agencyStatus,
			@RequestParam(required = false) String keyword,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		requireAdmin(principal);
		
		handle(
				() -> service.suspendArtist(
						userId,
						principal.getId(),
						request.getRemoteAddr()
				),
				"아티스트 계정을 정지했습니다.",
				redirectAttributes
		);
		
		addFilters(
				userStatus,
				agencyStatus,
				keyword,
				redirectAttributes
		);
		
		return "redirect:/admin/artists";
	}
	
	@PostMapping("/{userId}/reinstate")
	public String reinstateArtist(
			@PathVariable Long userId,
			@RequestParam(required = false) String userStatus,
			@RequestParam(required = false) String agencyStatus,
			@RequestParam(required = false) String keyword,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes
	) {
		requireAdmin(principal);
		
		handle(
				() -> service.reinstateArtist(
						userId,
						principal.getId(),
						request.getRemoteAddr()
				),
				"아티스트 계정의 정지를 해제했습니다.",
				redirectAttributes
		);
		
		addFilters(
				userStatus,
				agencyStatus,
				keyword,
				redirectAttributes
		);
		
		return "redirect:/admin/artists";
	}
	
	private void handle(
			Runnable action,
			String successMessage,
			RedirectAttributes redirectAttributes
	) {
		try {
			action.run();
			redirectAttributes.addFlashAttribute(
					"msg",
					successMessage
			);
		} catch (
				IllegalArgumentException
				| IllegalStateException e
		) {
			redirectAttributes.addFlashAttribute(
					"error",
					e.getMessage()
			);
		}
	}
	
	private void addFilters(
			String userStatus,
			String agencyStatus,
			String keyword,
			RedirectAttributes redirectAttributes
	) {
		addFilter(
				"userStatus",
				userStatus,
				redirectAttributes
		);
		
		addFilter(
				"agencyStatus",
				agencyStatus,
				redirectAttributes
		);
		
		addFilter(
				"keyword",
				keyword,
				redirectAttributes
		);
	}
	
	private void addFilter(
			String name,
			String value,
			RedirectAttributes redirectAttributes
	) {
		if (value != null && !value.isBlank()) {
			redirectAttributes.addAttribute(
					name,
					value.trim()
			);
		}
	}
	
	private void requireAdmin(
			AuthenticatedUser principal
	) {
		if (
				principal == null
						|| !"ROLE_ADMIN".equals(
						principal.getRoleName()
				)
		) {
			throw new IllegalStateException(
					"관리자 권한이 필요합니다."
			);
		}
	}
	
	private <E extends Enum<E>> E parseEnum(
			Class<E> enumType,
			String value
	) {
		if (value == null || value.isBlank()) {
			return null;
		}
		
		try {
			return Enum.valueOf(
					enumType,
					value.trim().toUpperCase()
			);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}