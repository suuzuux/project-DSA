package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminPartnershipApplicationResponse;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.*;
import megane6.weplanet.repository.PartnershipApplicationRepository;
import megane6.weplanet.repository.UserRepository;
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
	public PartnershipApplication approveApplication(
			Long applicationId,
			Long adminId,
			String ipAddress
	) {
		User admin = requireAdmin(adminId);
		PartnershipApplication application =
				requireApplication(applicationId);
		
		application.approve(admin);
		
		actionLogService.recordAction(
				adminId,
				AdminActionType.PARTNERSHIP_APPLICATION_APPROVE,
				AdminTargetType.PARTNERSHIP_APPLICATION,
				applicationId,
				application.getApplicantName() + " 등록 신청 승인",
				ipAddress
		);
		
		return application;
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
	
	public record ApplicationStats(
			long totalCount,
			long pendingCount,
			long approvedCount,
			long rejectedCount
	) {
	}
}