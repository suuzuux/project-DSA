package megane6.weplanet.controller.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminActionLogResponse;
import megane6.weplanet.domain.entity.enumfolder.AdminActionType;
import megane6.weplanet.domain.entity.enumfolder.AdminTargetType;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.admin.AdminActionLogService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
public class AdminActionLogController {
	
	private final AdminActionLogService service;
	
	@GetMapping
	public String logs(@RequestParam(required = false) String action,
					   @RequestParam(required = false) String targetType,
					   @RequestParam(required = false) Long actorId,
					   @RequestParam(required = false) Long targetId,
					   @RequestParam(required = false)
					   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
					   @RequestParam(required = false)
					   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
					   @RequestParam(required = false) String keyword,
					   @RequestParam(defaultValue = "0") int page,
					   @AuthenticationPrincipal AuthenticatedUser principal,
					   Model model) {
		requireAdmin(principal);
		AdminActionType actionFilter = parseEnum(AdminActionType.class, action);
		AdminTargetType targetTypeFilter = parseEnum(AdminTargetType.class, targetType);
		Page<AdminActionLogResponse> logs;
		
		try {
			logs = service.getLogs(
					actionFilter,
					targetTypeFilter,
					actorId,
					targetId,
					fromDate,
					toDate,
					keyword,
					page
			);
		} catch (IllegalArgumentException e) {
			model.addAttribute("error", e.getMessage());
			// 날짜 범위가 잘못되어도 페이지는 표시 (날짜 필터만 제외하고 다시 조회)
			logs = service.getLogs(
					actionFilter,
					targetTypeFilter,
					actorId,
					targetId,
					null,
					null,
					keyword,
					page
			);
		}
		model.addAttribute("logs", logs);
		model.addAttribute("stats", service.getStats());
		model.addAttribute("adminActors", service.getAdminActors());
		model.addAttribute("actionOptions", AdminActionType.values());
		model.addAttribute("targetTypeOptions", AdminTargetType.values());
		model.addAttribute("selectedAction",
				actionFilter == null ? "" : actionFilter.name());
		model.addAttribute("selectedTargetType",
				targetTypeFilter == null ? "" : targetTypeFilter.name());
		model.addAttribute("selectedActorId", actorId);
		model.addAttribute("selectedTargetId", targetId);
		model.addAttribute("fromDate", fromDate);
		model.addAttribute("toDate", toDate);
		model.addAttribute("keyword", keyword == null ? "" : keyword);
		
		return "admin/logs";
	}
	
	private void requireAdmin(AuthenticatedUser principal) {
		if (principal == null || !"ROLE_ADMIN".equals(principal.getRoleName())) {
			throw new IllegalStateException("관리자 권한이 필요합니다.");
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
			return Enum.valueOf(enumType, value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
