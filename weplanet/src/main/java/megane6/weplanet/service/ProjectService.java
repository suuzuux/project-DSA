package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ProjectRequestDTO;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.ProjectImage;
import megane6.weplanet.domain.entity.ProjectSettlementAccount;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanBadgeType;
import megane6.weplanet.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {
	// 프로젝트 본문 저장
	private final ProjectRepository pr;
	// 조건 (일반배지 5개 + 스페셜배지 1개)
	private final FanBadgeOwnershipRepository fbr;
	// 로그인 회원 조회 및 본인인증 확인
	private final UserRepository ur;
	// 대표 이미지 정보 저장
	private final ProjectImageRepository pir;
	// 정산계좌 저장
	private final ProjectSettlementAccountRepository psr;
	private static final long MIN_BASIC_BADGE_COUNT = 5L;
	private static final long MIN_SPECIAL_BADGE_COUNT = 1L;
	private final FileStorageService fs;
	private final AccountProtectionService aps;
	
	@Transactional
	public Long createProject(Long creatorId, ProjectRequestDTO dto) {
		// 1. 로그인 회원 조회
		User creator = ur.findById(creatorId).orElseThrow(() -> new IllegalArgumentException("로그인 정보를 찾을 수 없습니다."));
		
		// 2. 휴대폰 본인인증 여부 확인
		if (!creator.isPhoneVerified()) {
			throw new IllegalStateException("휴대폰 본인인증을 완료해야 프로젝트를 등록할 수 있습니다.");
		}
		
		// 3. 뱃지 개수 확인
		long basicBadgeCount = fbr.countByFan_IdAndGroupIdAndBadgeTypeAndRevokedAtIsNull(
				creator.getId(),
				dto.getGroupId(),
				FanBadgeType.BASIC
		);
		long specialBadgeCount = fbr.countByFan_IdAndGroupIdAndBadgeTypeAndRevokedAtIsNull(
				creator.getId(),
				dto.getGroupId(),
				FanBadgeType.SPECIAL
		);
		if (basicBadgeCount < MIN_BASIC_BADGE_COUNT ||  specialBadgeCount < MIN_SPECIAL_BADGE_COUNT) {
			throw new IllegalStateException("프로젝트 등록에는 기본 뱃지 5개 이상과 스페셜 뱃지 1개 이상이 필요합니다.");
		}
		
		// 4. Project 저장
		Project project = Project.createPending(
				dto.getGroupId(),
				creator,
				dto.getTitle(),
				dto.getEventType(),
				dto.getGoalAmount(),
				dto.getFundingStartAt(),
				dto.getFundingEndAt(),
				dto.getDescription(),
				Math.toIntExact(specialBadgeCount),
				Math.toIntExact(basicBadgeCount),
				creator.getPhoneVerifiedAt());
		Project savedProject = pr.save(project);
		
		// 5. 대표 이미지 저장
		MultipartFile coverImage = dto.getCoverImage();
		if (coverImage == null || coverImage.isEmpty()) {
			throw new IllegalArgumentException("대표 이미지를 등록해주세요.");
		}
		String contentType = coverImage.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new IllegalArgumentException("대표 이미지에는 이미지 파일만 등록 가능합니다.");
		}
		String originalName = coverImage.getOriginalFilename();
		if (originalName == null || originalName.isBlank()) {
			throw new IllegalArgumentException("대표 이미지의 파일명을 확인할 수 없습니다.");
		}
		
		// 실제 파일을 프로젝트의 uploads 폴더에 저장
		String storedName = fs.store(coverImage);
		// 파일 정보를 DB에 저장
		ProjectImage projectImage = ProjectImage.create(
				savedProject, originalName, storedName, contentType, coverImage.getSize()
		);
		pir.save(projectImage);
		
		// 6. 정산계좌 저장
		AccountProtectionService.ProtectedAccountNumber protectedAccount = aps.protect(dto.getAccountNumber());
		ProjectSettlementAccount settlementAccount = ProjectSettlementAccount.createUnverified(
				savedProject,
				dto.getSettlementBank(),
				protectedAccount.encrypted(),
				protectedAccount.hmac(),
				protectedAccount.last4()
		);
		psr.save(settlementAccount);
		
		// 7. 생성된 프로젝트 ID 반환
		return savedProject.getId();
	}
}
