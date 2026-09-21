package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminPartnershipApplicationResponse;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicantType;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicationStatus;
import megane6.weplanet.repository.PartnershipApplicationRepository;
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