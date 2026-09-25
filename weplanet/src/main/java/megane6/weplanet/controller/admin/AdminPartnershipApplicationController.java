package megane6.weplanet.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.admin.AdminPartnershipApplicationResponse;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicantType;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicationStatus;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.admin.AdminPartnershipApplicationService;
import megane6.weplanet.service.admin.AgencyAccountProvisioningService;
import megane6.weplanet.service.email.PartnershipInquiryService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/applications")
@RequiredArgsConstructor
public class AdminPartnershipApplicationController {
	
	private final AdminPartnershipApplicationService service;
	private final PartnershipInquiryService inquiryEmailService;
	
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
	
	@PostMapping("/{applicationId}/approve")
	public String approve(
			@PathVariable Long applicationId,
			
			// 승인 화면에서 관리자가 고친 소속사명 (비우면 신청서 이름 그대로)
			@RequestParam(required = false)
			String agencyName,
			
			@RequestParam(required = false)
			String status,
			
			@RequestParam(required = false)
			String applicantType,
			
			@RequestParam(required = false)
			String keyword,
			
			@RequestParam(defaultValue = "0")
			int page,
			
			HttpServletRequest request,
			
			@AuthenticationPrincipal
			AuthenticatedUser principal,
			
			RedirectAttributes redirectAttributes
	) {
		requireAdmin(principal);
		
		try {
			AdminPartnershipApplicationService.ApprovalResult result =
					service.approveApplication(
							applicationId,
							principal.getId(),
							agencyName,
							request.getRemoteAddr()
					);
			
			AgencyAccountProvisioningService.ProvisionedAccount account =
					result.account();
			
			// 메일은 승인 트랜잭션이 커밋된 뒤에 보낸다.
			// 메일이 실패해도 승인과 계정은 이미 저장된 상태라, 재발송으로 복구할 수 있다.
			try {
				inquiryEmailService.sendApprovalNotice(
						result.application(),
						account.username(),
						account.verificationKey(),
						account.rawToken(),
						account.expiresAt()
				);
				
				redirectAttributes.addFlashAttribute(
						"msg",
						"등록 신청을 승인하고 소속사 계정("
								+ account.username()
								+ ")을 발급했습니다. 활성화 안내 메일을 발송했습니다."
				);
			} catch (Exception mailException) {
				log.warn(
						"등록 신청 승인 이메일 발송 실패: applicationId={}",
						applicationId,
						mailException
				);
				
				redirectAttributes.addFlashAttribute(
						"msg",
						"등록 신청을 승인하고 소속사 계정("
								+ account.username()
								+ ")을 발급했지만, 활성화 메일 발송에 실패했습니다."
				);
			}
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute(
					"error",
					e.getMessage()
			);
		} catch (DataAccessException e) {
			// DB 제약 위반 같은 예상 못 한 저장 오류. 트랜잭션은 이미 롤백됐다
			log.error(
					"등록 신청 승인 중 DB 오류: applicationId={}",
					applicationId, e
			);
			
			redirectAttributes.addFlashAttribute(
					"error",
					"계정 발급 중 오류가 발생해 승인을 취소했습니다. 관리자 로그를 확인해주세요."
			);
		}
		
		addRedirectFilters(
				applicationId,
				status,
				applicantType,
				keyword,
				page,
				redirectAttributes
		);
		
		return "redirect:/admin/applications";
	}
	
	@PostMapping("/{applicationId}/reject")
	public String reject(
			@PathVariable Long applicationId,
			
			@RequestParam String rejectionReason,
			
			@RequestParam(required = false)
			String status,
			
			@RequestParam(required = false)
			String applicantType,
			
			@RequestParam(required = false)
			String keyword,
			
			@RequestParam(defaultValue = "0")
			int page,
			
			HttpServletRequest request,
			
			@AuthenticationPrincipal
			AuthenticatedUser principal,
			
			RedirectAttributes redirectAttributes
	) {
		requireAdmin(principal);
		
		try {
			PartnershipApplication application =
					service.rejectApplication(
							applicationId,
							principal.getId(),
							rejectionReason,
							request.getRemoteAddr()
					);
			
			try {
				inquiryEmailService.sendRejectionNotice(application);
				redirectAttributes.addFlashAttribute(
						"msg",
						"등록 신청을 반려하고 결과 이메일을 발송했습니다."
				);
			} catch (Exception mailException) {
				log.warn(
						"등록 신청 반려 이메일 발송 실패: applicationId={}",
						applicationId,
						mailException
				);
				
				redirectAttributes.addFlashAttribute(
						"msg",
						"등록 신청은 반려했지만 결과 이메일 발송에 실패했습니다."
				);
			}
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute(
					"error",
					e.getMessage()
			);
		}
		
		addRedirectFilters(
				applicationId,
				status,
				applicantType,
				keyword,
				page,
				redirectAttributes
		);
		
		return "redirect:/admin/applications";
	}
	
	private void addRedirectFilters(
			Long applicationId,
			String status,
			String applicantType,
			String keyword,
			int page,
			RedirectAttributes redirectAttributes
	) {
		redirectAttributes.addAttribute(
				"applicationId",
				applicationId
		);
		redirectAttributes.addAttribute(
				"page",
				Math.max(page, 0)
		);
		
		addRedirectFilter(
				"status",
				status,
				redirectAttributes
		);
		addRedirectFilter(
				"applicantType",
				applicantType,
				redirectAttributes
		);
		addRedirectFilter(
				"keyword",
				keyword,
				redirectAttributes
		);
	}
	
	private void addRedirectFilter(
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
