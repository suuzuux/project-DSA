package megane6.weplanet.service.agency;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicantType;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicationStatus;
import megane6.weplanet.repository.agency.PartnershipApplicationRepository;
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
			String message,
			Language applicantLanguage
	) {
		PartnershipApplication application = PartnershipApplication.createPending(
				applicantType,
				applicantName,
				contactName,
				email,
				phone,
				message,
				applicantLanguage
		);
		
		return par.save(application);
	}
	
	public PartnershipApplication getApplication(Long applicationId) {
		if (applicationId == null) {
			throw new IllegalArgumentException("partnership.error.idRequired");
		}
		
		return par.findDetailById(applicationId)
				.orElseThrow(() -> new IllegalArgumentException("partnership.error.notFound"));
	}
	
	public long countPendingApplications() {
		return par.countByStatus(PartnershipApplicationStatus.PENDING_APPROVAL);
	}
}
