package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Agency;
import megane6.weplanet.domain.entity.AgencyProfile;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.AgencyProfileRepository;
import megane6.weplanet.repository.AgencyRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.AgencyActivationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 입점 신청을 승인할 때 소속사 대표 계정 한 벌 만들어준다
 * Agency -> User (활성화 대기) -> AgencyProfile -> 활성화 토큰 순서로 만들고,
 * 하나라도 실패하면 승인 자체가 롤백되도록 호출하는 쪽 트랜잭션에 참여한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyAccountProvisioningService {
	
	private static final int NICKNAME_MAX_LENGTH = 50;
	private static final int CEO_NAME_MAX_LENGTH = 30;	// agencies.ceo_name 컬럼 같이
	
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
		// 신청서 이메일이 그대로 로그인 아이디가 된다
		String email = application.getEmail();
		if (ur.existsByUsername(email)) {
			throw new IllegalStateException("이미 사용 중인 로그인 아이디입니다: " + email);
		}
		
		if (ur.existsByEmail(email)) {
			throw new IllegalStateException("이미 가입된 이메일입니다: " + email);
		}
		
		String agencyName = resolveAgencyName(application, agencyNameOverride);
		
		// Agency.name은 unique라서 동명이인 1인 소속사가 들어오면 여기서 막힘
		// 관리자가 승인 화면에서 이름을 바꿔 다시 시도할 수 있도록 안내 문구를 담는다
		if (ar.findByName(agencyName).isPresent()) {
			throw new IllegalStateException("이미 등록된 소속사명입니다: " + agencyName +
					" (승인 화면에서 다른 이름으로 수정해주세요.)");
		}
		
		Agency agency = ar.save(
				Agency.create(
						agencyName,
						// 사업자번호는 소속사가 나중에 직접 등록
						null,
						// 신청서 담당자명은 50자까지만 받지만, 대표자명 컬럼은 30자라 잘라서 넣는다
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
	
	// 관리자가 승인 화면에서 소속사며을 직접 고쳐 보낼 수 있다
	// 비워두면 신청서에 적힌 이름을 그대로 쓴다.
	// (아티스트 개인 신청도 1인 소속사로 만들기 때문에 처리 방식이 같다.)
	private String resolveAgencyName(PartnershipApplication application, String agencyNameOverride) {
		if (agencyNameOverride != null && !agencyNameOverride.isBlank()) {
			return agencyNameOverride.trim();
		}
		
		return application.getApplicantName();
	}
	
	// nickname은 50자 제한이라 소속사명(최대 100자)을 그대로 넣을 수 없다
	// 중복될 때는 신청 번호를 붙여 구분한다.
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

