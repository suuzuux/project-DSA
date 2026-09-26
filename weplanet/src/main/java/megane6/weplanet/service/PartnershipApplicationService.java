package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicantType;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicationStatus;
import megane6.weplanet.repository.PartnershipApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnershipApplicationService {
	
	private final PartnershipApplicationRepository par;
	
	@Transactional
	public PartnershipApplication submit(
			PartnershipApplicantType applicantType,
			String applicantName,
			String contactName,
			String email,
			String phone,
			String message
	) {
		PartnershipApplication application = PartnershipApplication.createPending(
				applicantType,
				applicantName,
				contactName,
				email,
				phone,
				message
		);
		
		return par.save(application);
	}
	
	public PartnershipApplication getApplication(Long applicationId) {
		if (applicationId == null) {
			throw new IllegalArgumentException("신청 번호가 필요합니다.");
		}
		
		return par.findDetailById(applicationId)
				.orElseThrow(() -> new IllegalArgumentException("입점 신청을 찾을 수 없습니다."));
	}
	
	public long countPendingApplications() {
		return par.countByStatus(PartnershipApplicationStatus.PENDING_APPROVAL);
	}
}
