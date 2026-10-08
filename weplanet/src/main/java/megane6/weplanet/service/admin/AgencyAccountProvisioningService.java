package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Agency;
import megane6.weplanet.domain.entity.AgencyProfile;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.exception.LocalizedIllegalStateException;
import megane6.weplanet.repository.agency.AgencyProfileRepository;
import megane6.weplanet.repository.agency.AgencyRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.agency.AgencyActivationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 입점 승인 시 소속사 대표 계정 생성 (Agency → User → AgencyProfile → 토큰, 승인 트랜잭션에 참여). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyAccountProvisioningService {
	
	private static final int NICKNAME_MAX_LENGTH = 50;
	private static final int CEO_NAME_MAX_LENGTH = 30;	// agencies.ceo_name 컬럼 길이
	
	private final UserRepository ur;
	private final AgencyRepository ar;
	private final AgencyProfileRepository apr;
	private final AgencyActivationService aas;
	
	@Transactional
	public ProvisionedAccount provision(
			PartnershipApplication application,
			User admin,
			String agencyNameOverride
	) {
		// 신청서 이메일이 로그인 아이디가 된다.
		String email = application.getEmail();
		// 예외는 키 + 값으로 던진다 (컨트롤러가 번역).
		if (ur.existsByUsername(email)) {
			throw new LocalizedIllegalStateException("admin.error.partnership.usernameTaken", email);
		}
		
		if (ur.existsByEmail(email)) {
			throw new LocalizedIllegalStateException("admin.error.partnership.emailRegistered", email);
		}
		
		String agencyName = resolveAgencyName(application, agencyNameOverride);
		
		// 소속사명은 unique 라 겹치면 관리자가 이름을 바꿔 다시 시도하도록 안내한다.
		if (ar.findByName(agencyName).isPresent()) {
			log.info("입점 승인 중 소속사명 중복: applicationId={}, agencyName={}", application.getId(), agencyName);
			throw new LocalizedIllegalStateException("admin.error.partnership.agencyNameTaken", agencyName);
		}
		
		Agency agency = ar.save(
				Agency.create(
						agencyName,
						// 사업자번호는 소속사가 나중에 등록
						null,
						// 대표자명 컬럼은 30자라 잘라서 넣는다.
						truncate(application.getContactName(), CEO_NAME_MAX_LENGTH)
				)
		);
		
		User owner = ur.save(
				User.createPendingAgencyOwner(
						email,				// 로그인 아이디
						application.getContactName(),
						resolveNickname(agencyName, application.getId()),
						email
				)
		);
		
		owner.assignAgency(agency);
		apr.save(AgencyProfile.createApprovedOwner(owner, agency, admin));
		
		AgencyActivationService.IssuedActivation issued =
				aas.issueActivationToken(owner);
		
		log.info("소속사 계정 발급 완료: applicationId={}, userId={}, agencyId={}",
				application.getId(), owner.getId(), agency.getId());
		
		return new ProvisionedAccount(
				owner.getId(),
				owner.getUsername(),
				agency.getName(),
				issued.verificationKey(),
				issued.rawToken(),
				issued.expiresAt()
		);
	}
	
	// 관리자가 고친 소속사명 (비우면 신청서 이름, 개인 신청도 1인 소속사로 처리).
	private String resolveAgencyName(PartnershipApplication application, String agencyNameOverride) {
		if (agencyNameOverride != null && !agencyNameOverride.isBlank()) {
			return agencyNameOverride.trim();
		}
		
		return application.getApplicantName();
	}
	
	// nickname 은 50자라 잘라 쓰고, 중복이면 신청 번호를 붙인다.
	private String resolveNickname(String agencyName, Long applicationId) {
		String candidate = truncate(agencyName, NICKNAME_MAX_LENGTH);
		
		if (!ur.existsByNickname(candidate)) {
			return candidate;
		}
		
		String suffix = "-" + applicationId;
		
		return truncate(candidate, NICKNAME_MAX_LENGTH - suffix.length()) + suffix;
	}
	
	private String truncate(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		
		return value.substring(0, maxLength);
	}
	
	// 승인 결과 화면과 초대 메일에 필요한 정보
	public record ProvisionedAccount(
			Long userId,
			String username,
			String agencyName,
			String verificationKey,
			String rawToken,
			LocalDateTime expiresAt
	) {}
}

