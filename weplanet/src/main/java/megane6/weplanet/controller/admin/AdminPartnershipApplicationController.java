package megane6.weplanet.controller.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminPartnershipApplicationResponse;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicantType;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicationStatus;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.admin.AdminPartnershipApplicationService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/applications")
@RequiredArgsConstructor
public class AdminPartnershipApplicationController {
	
	private final AdminPartnershipApplicationService service;
	
	@GetMapping
	public String applications(@RequestParam(required = false) String status,
							   @RequestParam(required = false) String applicantType,
							   @RequestParam(required = false) String keyword,
							   @RequestParam(required = false) Long applicationId,
							   @RequestParam(defaultValue = "0") int page,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   Model model) {
		requireAdmin(principal);
		
		PartnershipApplicationStatus statusFilter = parseEnum(
				PartnershipApplicationStatus.class,
				status
		);
		
		PartnershipApplicantType applicantTypeFilter = parseEnum(
				PartnershipApplicantType.class,
				applicantType
		);
		
		Page<AdminPartnershipApplicationResponse> applications =
				service.getApplications(
						statusFilter,
						applicantTypeFilter,
						keyword,
						page
				);
		
		model.addAttribute("applications", applications);
		model.addAttribute("stats", service.getStats());
		model.addAttribute("statusOptions", PartnershipApplicationStatus.values());
		model.addAttribute("applicantTypeOptions",  PartnershipApplicantType.values());
		model.addAttribute("selectedStatus", statusFilter == null ? "" : statusFilter.name());
		model.addAttribute("selectedApplicationType", applicantTypeFilter == null ? "" : applicantTypeFilter.name());
		model.addAttribute("keyword", keyword == null ? "" : keyword);
		
		if (applicationId != null) {
			try {
				model.addAttribute("selectedApplication", service.getApplication(applicationId));
			} catch (IllegalArgumentException e) {
				model.addAttribute("error", e.getMessage());
			}
		}
		
		return "admin/applications";
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
