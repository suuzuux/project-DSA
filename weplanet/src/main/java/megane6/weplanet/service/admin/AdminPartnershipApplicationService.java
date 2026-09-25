package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminPartnershipApplicationResponse;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.*;
import megane6.weplanet.repository.PartnershipApplicationRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.AgencyActivationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPartnershipApplicationService {
	
	private static final int PAGE_SIZE = 20;
	
	private final PartnershipApplicationRepository applicationRepository;
	private final UserRepository userRepository;
	private final AdminActionLogService actionLogService;
	
	private final AdminActionLogService aas;
	private final AgencyAccountProvisioningService provisioningService;
	private final AgencyActivationService activationService;
	
	public Page<AdminPartnershipApplicationResponse> getApplications(
			PartnershipApplicationStatus status,
			PartnershipApplicantType applicantType,
			String keyword,
			int page
	) {
		String normalizedKeyword =
				normalizeKeyword(keyword);
		
		int safePage = Math.max(page, 0);
		
		return applicationRepository.searchForAdmin(
				status,
				applicantType,
				normalizedKeyword,
				PageRequest.of(safePage, PAGE_SIZE)
		).map(this::toResponse);
	}
	
	public AdminPartnershipApplicationResponse getApplication(
			Long applicationId
	) {
		if (applicationId == null) {
			throw new IllegalArgumentException(
					"신청 번호가 필요합니다."
			);
		}
		
		PartnershipApplication application =
				applicationRepository
						.findDetailById(applicationId)
						.orElseThrow(() ->
								new IllegalArgumentException(
										"입점 신청을 찾을 수 없습니다."
								)
						);
		
		return toResponse(application);
	}
	
	public ApplicationStats getStats() {
		return new ApplicationStats(
				applicationRepository.count(),
				applicationRepository.countByStatus(
						PartnershipApplicationStatus.PENDING_APPROVAL
				),
				applicationRepository.countByStatus(
						PartnershipApplicationStatus.APPROVED
				),
				applicationRepository.countByStatus(
						PartnershipApplicationStatus.REJECTED
				)
		);
	}
	
	@Transactional
	public ApprovalResult approveApplication(
			Long applicationId,
			Long adminId,
			String agencyName,
			String ipAddress
	) {
		User admin = requireAdmin(adminId);
		PartnershipApplication application = requireApplication(applicationId);
		
		application.approve(admin);
		
		// 계정 발급이 실패하면 승인 자체가 롤백됨
		// "승인은 됐는데 계정이 없는" 상태를 만들기 않기 위해서
		AgencyAccountProvisioningService.ProvisionedAccount account
				= provisioningService.provision(application, admin, agencyName);
		
		aas.recordAction(
				adminId,
				AdminActionType.PARTNERSHIP_APPLICATION_APPROVE,
				AdminTargetType.PARTNERSHIP_APPLICATION,
				applicationId,
				application.getApplicantName() + " 등록 신청 승인 (소속사 계정 발급: "
				+ account.username() + ")",
				ipAddress
		);
		
		return new ApprovalResult(application, account);
	}
	
	@Transactional
	public PartnershipApplication rejectApplication(
			Long applicationId,
			Long adminId,
			String rejectionReason,
			String ipAddress
	) {
		User admin = requireAdmin(adminId);
		PartnershipApplication application =
				requireApplication(applicationId);
		
		application.reject(admin, rejectionReason);
		
		actionLogService.recordAction(
				adminId,
				AdminActionType.PARTNERSHIP_APPLICATION_REJECT,
				AdminTargetType.PARTNERSHIP_APPLICATION,
				applicationId,
				application.getApplicantName()
						+ " 등록 신청 반려: "
						+ application.getRejectionReason(),
				ipAddress
		);
		
		return application;
	}
	
	@Transactional
	public ResendResult resendActivation(
			Long applicationId, Long adminId, String ipAddress) {
		requireAdmin(adminId);
		PartnershipApplication application = requireApplication(applicationId);
		
		if (application.getStatus() != PartnershipApplicationStatus.APPROVED) {
			throw new IllegalStateException("승인된 신청만 활성화 메일을 재발송할 수 있습니다.");
		}
		
		// 승인할 때 신청서 이메일을 그대로 로그인 아이디로 만들었으므로, 같은 값으로 찾는다.
		User agencyUser = userRepository.findByUsername(application.getEmail())
				.orElseThrow(() -> new IllegalStateException("이 신청으로 발급된 소속사 계정을 찾을 수 없습니다: "
						+ application.getEmail()));
		
		AgencyActivationService.IssuedActivation issuedActivation
				= activationService.reissueActivationToken(agencyUser);
		
		actionLogService.recordAction(
				adminId,
				AdminActionType.PARTNERSHIP_ACTIVATION_RESEND,
				AdminTargetType.PARTNERSHIP_APPLICATION,
				applicationId,
				application.getApplicantName()
						+ " 소속사 계정 활성화 메일 재발송 ("
						+ agencyUser.getUsername()
						+ ")",
				ipAddress
		);
		
		return new ResendResult(
				application,
				agencyUser.getUsername(),
				issuedActivation
		);
	}
	
	private PartnershipApplication requireApplication(
			Long applicationId
	) {
		if (applicationId == null) {
			throw new IllegalArgumentException(
					"신청 번호가 필요합니다."
			);
		}
		
		return applicationRepository.findById(applicationId)
				.orElseThrow(() ->
						new IllegalArgumentException(
								"입점 신청을 찾을 수 없습니다."
						)
				);
	}
	
	private User requireAdmin(Long adminId) {
		User admin = userRepository.findById(adminId)
				.orElseThrow(() ->
						new IllegalArgumentException(
								"관리자 계정을 찾을 수 없습니다."
						)
				);
		
		if (admin.getRole() != Role.ADMIN) {
			throw new IllegalStateException(
					"관리자 권한이 필요합니다."
			);
		}
		
		return admin;
	}
	
	private AdminPartnershipApplicationResponse toResponse(
			PartnershipApplication application
	) {
		User reviewer = application.getReviewedBy();
		
		return new AdminPartnershipApplicationResponse(
				application.getId(),
				
				application.getApplicantType().name(),
				application
						.getApplicantType()
						.getDisplayName(),
				
				application.getApplicantName(),
				application.getContactName(),
				application.getEmail(),
				application.getPhone(),
				application.getMessage(),
				
				application.getStatus().name(),
				application
						.getStatus()
						.getDisplayName(),
				
				reviewer == null
						? null
						: reviewer.getId(),
				
				reviewer == null
						? null
						: reviewer.getNickname(),
				
				application.getReviewedAt(),
				
				application.getRejectionReason(),
				
				application.getCreatedAt(),
				application.getUpdatedAt()
		);
	}
	
	private String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		
		return keyword.trim();
	}
	
	// 승인 결과. 컨트롤러가 이 정보로 초대 메일을 보낸다.
	public record ApprovalResult(
			PartnershipApplication application,
			AgencyAccountProvisioningService.ProvisionedAccount account
	) {}
	
	// 재발송 결과. 컨트롤러가 이 정보로 새 링크를 메일로 보낸다.
	public record ResendResult(
			PartnershipApplication application,
			String username,
			AgencyActivationService.IssuedActivation activation
	) {}
	
	public record ApplicationStats(
			long totalCount,
			long pendingCount,
			long approvedCount,
			long rejectedCount
	) {
	}
}