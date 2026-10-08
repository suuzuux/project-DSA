package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.*;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.ProjectImage;
import megane6.weplanet.domain.entity.ProjectSettlementAccount;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.*;
import megane6.weplanet.repository.*;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.admin.AdminActionLogService;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.email.MailSenderService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {
	// 프로젝트 저장
	private final ProjectRepository pr;
	// 배지 조건 (일반 5개 + 스페셜 1개)
	private final FanBadgeOwnershipRepository fbr;
	// 회원 조회
	private final UserRepository ur;
	// 대표 이미지
	private final ProjectImageRepository pir;
	// 정산계좌
	private final ProjectSettlementAccountRepository psr;
	private final FileStorageService fs;
	private final AccountProtectionService aps;
	private final FanProjectCommunityAccessRepository fcr;
	private final ProjectContributionRepository pcr;
	
	// 이메일 인증
	private final EmailVerificationService evs;
	private final MailSenderService mss;

	private final AdminActionLogService actionLogService;

	// 라벨과 자격 안내 문구를 현재 로케일로 만든다.
	private final megane6.weplanet.i18n.Messages messages;
	private final CommunityArtistResolver communityArtistResolver;
	
	public static final long MIN_BASIC_BADGE_COUNT = 5L;
	public static final long MIN_SPECIAL_BADGE_COUNT = 1L;

	// 목록 정렬 기준 (화면 select 값과 짝)
	public static final String SORT_DEADLINE = "deadline";
	public static final String SORT_LATEST = "latest";

	/** 등록 인증번호 발급·발송 (발송 실패 시 인증 기록도 롤백, 인증 키 반환). */
	@Transactional
	public String sendProjectVerificationCode(Long userId) {
		EmailVerificationService.IssuedVerification issued = evs.issueProjectVerification(userId);

		mss.sendProjectVerificationCode(
				issued.recipientEmail(),
				issued.rawCode(),
				EmailVerificationService.EXPIRATION_MINUTES
		);

		return issued.verificationKey();
	}

	/** 커뮤니티별 프로젝트 카드 (비로그인 공개만, 팬은 공개 + 본인 것, 관리자 전체). */
	public List<ProjectCardView> getProjectCards(User artist, String sort, AuthenticatedUser viewer) {
		assertProjectAreaAccessible(artist, viewer);

		List<Project> projects = pr.findByArtistAndDeletedAtIsNull(artist).stream()
				.filter(project -> canView(project, viewer))
				.sorted(projectComparator(sort))
				.toList();

		if (projects.isEmpty()) {
			return List.of();
		}

		// 대표 이미지를 한 번에 조회한다.
		List<Long> projectIds = projects.stream().map(Project::getId).toList();
		Map<Long, String> coverNames = pir.findByProject_IdIn(projectIds).stream()
				.collect(Collectors.toMap(
						image -> image.getProject().getId(),
						ProjectImage::getStoredName,
						(first, second) -> first
				));
		Map<Long, ProjectFundingSummary> fundingSummaries = pcr.summarizePaidByProjectIds(
				projectIds,
				FanProjectPaymentStatus.PAID
		).stream().collect(Collectors.toMap(
				ProjectFundingSummary::projectId,
				summary -> summary
		));

		return projects.stream()
				.map(project -> {
					ProjectFundingSummary summary = fundingSummaries.getOrDefault(
							project.getId(),
							new ProjectFundingSummary(project.getId(), 0L, 0L)
					);
					return ProjectCardView.from(
							project,
							coverNames.get(project.getId()),
							summary.fundedAmount(),
							summary.participantCount(),
							messages.get(project.getEventType().getMessageKey()),
							messages.get(project.getStatus().getMessageKey())
					);
				})
				.toList();
	}

	public ProjectDetailView getProjectDetail(Long projectId, User artist, AuthenticatedUser viewer) {
		assertProjectAreaAccessible(artist, viewer);
		Project project = pr.findById(projectId).orElseThrow(() -> new IllegalArgumentException("error.project.notFound"));

		// 소프트 삭제된 프로젝트는 없는 것으로 본다.
		if (project.getDeletedAt() != null) {
			throw new IllegalArgumentException("error.project.deleted");
		}
		if (!project.getArtist().getId().equals(artist.getId())) {
			throw new IllegalArgumentException("error.project.notInCommunity");
		}
		if (!canView(project, viewer)) {
			throw new IllegalStateException("error.project.noPermission");
		}

		String coverStoredName = pir.findByProject_Id(projectId)
				.map(ProjectImage::getStoredName)
				.orElse(null);
		ProjectFundingSummary fundingSummary = pcr.summarizePaidByProjectIds(
				List.of(projectId),
				FanProjectPaymentStatus.PAID
		).stream().findFirst().orElse(
				new ProjectFundingSummary(projectId, 0L, 0L)
		);

		return ProjectDetailView.from(
				project,
				coverStoredName,
				fundingSummary.fundedAmount(),
				fundingSummary.participantCount(),
				messages.get(project.getEventType().getMessageKey()),
				messages.get(project.getStatus().getMessageKey())
		);
	}

	/** 프로젝트 영역 접근 확인 (본인 커뮤니티 아티스트·소속사 불가, 관리자 전체). */
	public void assertProjectAreaAccessible(
			User artist,
			AuthenticatedUser viewer
	) {
		if (viewer == null) {
			throw new AccessDeniedException("common.error.loginRequired");
		}

		if (hasRole(viewer, Role.AGENCY)) {
			throw new AccessDeniedException("error.project.agencyNotAllowed");
		}

		if (hasRole(viewer, Role.ADMIN)) {
			return;
		}
		
		User member = ur.findById(viewer.getId())
				.orElseThrow(() ->
						new AccessDeniedException("error.project.memberNotFound")
				);
		
		// 자기 커뮤니티 아티스트는 팬 프로젝트를 이용할 수 없다.
		if (communityArtistResolver.isArtistOf(member, artist.getId())) {
			throw new AccessDeniedException("error.project.ownCommunity");
		}
		
		if (!member.canParticipateInCommunity()) {
			throw new AccessDeniedException("error.project.fanOrArtistOnly");
		}
		
		if (!fcr.existsByFanIdAndArtistId(member.getId(), artist.getId())) {
			throw new AccessDeniedException("error.project.joinFirst");
		}
	}
	
	@Transactional
	public void approveProject(
			Long projectId,
			Long artistId,
			Long adminId,
			String ipAddress
	) {
		User admin = getAdmin(adminId);
		
		Project project =
				getProjectInArtistCommunity(
						projectId,
						artistId
				);
		
		project.approve(admin);
		
		actionLogService.recordAction(
				adminId,
				AdminActionType.PROJECT_APPROVE,
				AdminTargetType.PROJECT,
				project.getId(),
				project.getTitle() + " 프로젝트 승인",
				ipAddress
		);
	}
	
	@Transactional
	public void rejectProject(
			Long projectId,
			Long artistId,
			Long adminId,
			String rejectionReason,
			String ipAddress
	) {
		User admin = getAdmin(adminId);
		
		Project project =
				getProjectInArtistCommunity(
						projectId,
						artistId
				);
		
		project.reject(
				admin,
				rejectionReason
		);
		
		actionLogService.recordAction(
				adminId,
				AdminActionType.PROJECT_REJECT,
				AdminTargetType.PROJECT,
				project.getId(),
				project.getRejectionReason(),
				ipAddress
		);
	}

	private boolean canView(Project project, AuthenticatedUser viewer) {
		if (hasRole(viewer, Role.ADMIN)) {
			return true;
		}
		if (project.getStatus().isPubliclyVisible()) {
			return true;
		}
		return (hasRole(viewer, Role.FAN) || hasRole(viewer, Role.ARTIST) || hasRole(viewer, Role.ARTIST_MEMBER))
				&& project.getCreator().getId().equals(viewer.getId());
	}

	private Comparator<Project> projectComparator(String sort) {
		if (SORT_LATEST.equals(sort)) {
			return Comparator.comparing(Project::getCreatedAt).reversed();
		}

		LocalDateTime now = LocalDateTime.now();
		return Comparator
				.comparing((Project project) -> project.getFundingEndAt().isBefore(now))
				.thenComparing(Project::getFundingEndAt);
	}

	private Project getProjectInArtistCommunity(Long projectId, Long artistId) {
		Project project = pr.findById(projectId)
				.orElseThrow(() -> new IllegalArgumentException("error.project.notFound"));
		if (project.getDeletedAt() != null || !project.getArtist().getId().equals(artistId)) {
			throw new IllegalArgumentException("error.project.notInCommunity");
		}
		return project;
	}

	private User getAdmin(Long adminId) {
		return ur.findById(adminId)
				.filter(user -> user.getRole() == Role.ADMIN)
				.orElseThrow(() -> new IllegalStateException("error.project.adminOnlyReview"));
	}

	private boolean hasRole(AuthenticatedUser viewer, Role role) {
		return viewer != null && role.authority().equals(viewer.getRoleName());
	}
	
	/** 등록 자격(배지 수) 확인 - 화면 버튼과 등록 처리가 같은 메서드를 쓴다. */
	@Transactional(readOnly = true)
	public ProjectEligibilityView checkEligibility(Long fanId, Long artistId) {
		long basicBadgeCount = fbr.countByFan_IdAndArtist_IdAndBadgeTypeAndRevokedAtIsNull(
				fanId, artistId, FanBadgeType.BASIC);
		long specialBadgeCount = fbr.countByFan_IdAndArtist_IdAndBadgeTypeAndRevokedAtIsNull(
				fanId, artistId, FanBadgeType.SPECIAL);
		boolean eligible = basicBadgeCount >= MIN_BASIC_BADGE_COUNT
				&& specialBadgeCount >= MIN_SPECIAL_BADGE_COUNT;
		String message = eligible ? null : messages.get(
				"error.project.badgeShortage",
				MIN_BASIC_BADGE_COUNT, MIN_SPECIAL_BADGE_COUNT, basicBadgeCount, specialBadgeCount);
		
		return new ProjectEligibilityView(
				eligible,
				basicBadgeCount,
				specialBadgeCount,
				MIN_BASIC_BADGE_COUNT,
				MIN_SPECIAL_BADGE_COUNT,
				message
		);
	}
	
	@Transactional
	public Long createProject(Long creatorId, ProjectRequestDTO dto) {
		// 1. 회원 조회
		User creator = ur.findById(creatorId).orElseThrow(() -> new IllegalArgumentException("error.project.memberNotFound"));
		if (!creator.canParticipateInCommunity()) {
			throw new IllegalStateException("error.project.createFanOrArtistOnly");
		}

		User artist = ur.findById(dto.getArtistId())
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));

		if (communityArtistResolver.isArtistOf(creator, artist.getId())) {
			throw new AccessDeniedException("error.project.createOwnCommunity");
		}
		
		if (!fcr.existsByFanIdAndArtistId(
				creator.getId(),
				artist.getId()
		)) {
			throw new AccessDeniedException("error.project.joinFirst");
		}
		
		// 3. 배지 수 확인
		ProjectEligibilityView eligibility = checkEligibility(creator.getId(), artist.getId());
		if (!eligibility.eligible()) {
			throw new IllegalStateException("error.project.badgeRequirement");
		}
		
		long basicBadgeCount = eligibility.basicCount();
		long specialBadgeCount = eligibility.specialCount();
		
		LocalDateTime emailVerifiedAt = evs.consumeProjectVerification(
				creator.getId(),
				dto.getEmailVerificationKey()
		);
		
		// 4. 저장 - 날짜에 시각을 붙인다 (23:59:59.999999999 는 MySQL 반올림으로 다음 날이 되어 초 단위 사용).
		LocalDateTime fundingStartAt = dto.getFundingStartAt().atStartOfDay();
		LocalDateTime fundingEndAt = dto.getFundingEndAt().atTime(LocalTime.of(23, 59, 59));

		Project project = Project.createPending(
				artist,
				creator,
				dto.getTitle(),
				dto.getEventType(),
				dto.getGoalAmount(),
				fundingStartAt,
				fundingEndAt,
				dto.getDescription(),
				Math.toIntExact(specialBadgeCount),
				Math.toIntExact(basicBadgeCount),
				emailVerifiedAt);
		Project savedProject = pr.save(project);
		
		// 5. 대표 이미지 저장
		MultipartFile coverImage = dto.getCoverImage();
		if (coverImage != null && !coverImage.isEmpty()) {
			String contentType = coverImage.getContentType();
			if (contentType == null || !contentType.startsWith("image/")) {
				throw new IllegalArgumentException("error.project.coverImageOnly");
			}
			String originalName = coverImage.getOriginalFilename();
			if (originalName == null || originalName.isBlank()) {
				throw new IllegalArgumentException("error.project.coverFileName");
			}
			// uploads 폴더에 저장
			String storedName = fs.store(coverImage);
			// 파일 정보 저장
			ProjectImage projectImage = ProjectImage.create(
					savedProject, originalName, storedName, contentType, coverImage.getSize()
			);
			pir.save(projectImage);
		}
		
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
		
		// 7. 프로젝트 ID 반환
		return savedProject.getId();
	}
}
