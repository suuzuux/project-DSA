package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ProjectCardView;
import megane6.weplanet.domain.dto.ProjectDetailView;
import megane6.weplanet.domain.dto.ProjectRequestDTO;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.ProjectImage;
import megane6.weplanet.domain.entity.ProjectSettlementAccount;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanBadgeType;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.*;
import megane6.weplanet.security.AuthenticatedUser;
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
	private final FanProjectCommunityAccessRepository fcr;
	
	// 이메일
	private final EmailVerificationService evs;

	// 목록 정렬 기준 - 화면 select의 value와 짝을 이룸
	public static final String SORT_DEADLINE = "deadline";
	public static final String SORT_LATEST = "latest";

	/**
	 * 커뮤니티(아티스트)별 프로젝트 목록을 카드용 DTO로 만들어 돌려준다.
	 * 비로그인 사용자는 공개 상태만, FAN은 공개 상태와 자신이 만든 프로젝트,
	 * ADMIN은 모든 상태를 확인한다. ARTIST와 AGENCY는 프로젝트 영역에 접근할 수 없다.
	 * DTO 변환을 서비스 안에서 끝내는 이유 : Project.creator가 LAZY라서
	 * 트랜잭션 밖에서 getNickname()을 부르면 LazyInitializationException이 난다.
	 */
	public List<ProjectCardView> getProjectCards(User artist, String sort, AuthenticatedUser viewer) {
		assertProjectAreaAccessible(artist, viewer);

		List<Project> projects = pr.findByArtistAndDeletedAtIsNull(artist).stream()
				.filter(project -> project.getStatus().isPubliclyVisible())
				.sorted(projectComparator(sort))
				.toList();

		if (projects.isEmpty()) {
			return List.of();
		}

		// 대표 이미지는 프로젝트당 1장. 목록 전체를 쿼리 한 번으로 가져와 id로 찾아 쓴다.
		List<Long> projectIds = projects.stream().map(Project::getId).toList();
		Map<Long, String> coverNames = pir.findByProject_IdIn(projectIds).stream()
				.collect(Collectors.toMap(
						image -> image.getProject().getId(),
						ProjectImage::getStoredName,
						(first, second) -> first
				));

		return projects.stream()
				.map(project -> ProjectCardView.from(project, coverNames.get(project.getId())))
				.toList();
	}

	public ProjectDetailView getProjectDetail(Long projectId, User artist, AuthenticatedUser viewer) {
		assertProjectAreaAccessible(artist, viewer);
		Project project = pr.findById(projectId).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));

		// 소프트 삭제된 프로젝트는 없는 것으로 취급(목록 쿼리의 deletedAt IS NULL과 같은 기준)
		if (project.getDeletedAt() != null) {
			throw new IllegalArgumentException("삭제된 프로젝트입니다.");
		}
		if (!project.getArtist().getId().equals(artist.getId())) {
			throw new IllegalArgumentException("이 커뮤니티의 프로젝트가 아닙니다.");
		}
		if (!canView(project, viewer)) {
			throw new IllegalStateException("이 프로젝트를 확인할 권한이 없습니다.");
		}

		String coverStoredName = pir.findByProject_Id(projectId)
				.map(ProjectImage::getStoredName)
				.orElse(null);

		return ProjectDetailView.from(project, coverStoredName);
	}

	/**
	 * ARTIST와 AGENCY는 팬 프로젝트 메뉴 및 직접 URL 접근을 허용하지 않는다.
	 * 비로그인 사용자, FAN, ADMIN은 이후 상태별 공개 규칙에 따라 접근한다.
	 */
	public void assertProjectAreaAccessible(
			User artist,
			AuthenticatedUser viewer
	) {
		if (viewer == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}
		
		if (hasRole(viewer, Role.ARTIST) || hasRole(viewer, Role.AGENCY)) {
			throw new AccessDeniedException(
					"아티스트와 소속사 계정은 팬 프로젝트를 확인할 수 없습니다."
			);
		}
		
		// ADMIN은 커뮤니티 가입 여부와 관계없이 심사를 위해 접근 가능
		if (hasRole(viewer, Role.ADMIN)) {
			return;
		}
		
		if (!hasRole(viewer, Role.FAN)) {
			throw new AccessDeniedException("팬 회원만 접근할 수 있습니다.");
		}
		
		User fan = ur.findById(viewer.getId())
				.orElseThrow(() ->
						new AccessDeniedException("로그인 회원을 찾을 수 없습니다.")
				);
		
		if (!fcr.existsByFanIdAndArtistId(
				fan.getId(),
				artist.getId()
		)) {
			throw new AccessDeniedException("먼저 커뮤니티에 가입해주세요.");
		}
	}

	@Transactional
	public void approveProject(Long projectId, Long artistId, Long adminId) {
		User admin = getAdmin(adminId);
		Project project = getProjectInArtistCommunity(projectId, artistId);
		project.approve(admin);
	}

	@Transactional
	public void rejectProject(Long projectId, Long artistId, Long adminId, String rejectionReason) {
		User admin = getAdmin(adminId);
		Project project = getProjectInArtistCommunity(projectId, artistId);
		project.reject(admin, rejectionReason);
	}

	private boolean canView(Project project, AuthenticatedUser viewer) {
		if (hasRole(viewer, Role.ADMIN)) {
			return true;
		}
		if (project.getStatus().isPubliclyVisible()) {
			return true;
		}
		return hasRole(viewer, Role.FAN)
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
				.orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
		if (project.getDeletedAt() != null || !project.getArtist().getId().equals(artistId)) {
			throw new IllegalArgumentException("이 커뮤니티의 프로젝트를 찾을 수 없습니다.");
		}
		return project;
	}

	private User getAdmin(Long adminId) {
		return ur.findById(adminId)
				.filter(user -> user.getRole() == Role.ADMIN)
				.orElseThrow(() -> new IllegalStateException("ADMIN만 프로젝트를 승인하거나 반려할 수 있습니다."));
	}

	private boolean hasRole(AuthenticatedUser viewer, Role role) {
		return viewer != null && role.authority().equals(viewer.getRoleName());
	}

	@Transactional
	public Long createProject(Long creatorId, ProjectRequestDTO dto) {
		// 1. 로그인 회원 조회
		User creator = ur.findById(creatorId).orElseThrow(() -> new IllegalArgumentException("로그인 정보를 찾을 수 없습니다."));
		if (creator.getRole() != Role.FAN) {
			throw new IllegalStateException("팬 회원만 프로젝트를 등록할 수 있습니다.");
		}

		User artist = ur.findById(dto.getArtistId())
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트 정보를 찾을 수 없습니다."));
		
		if (!fcr.existsByFanIdAndArtistId(
				creator.getId(),
				artist.getId()
		)) {
			throw new AccessDeniedException("먼저 커뮤니티에 가입해주세요.");
		}
		
		// 3. 뱃지 개수 확인
		long basicBadgeCount = fbr.countByFan_IdAndArtist_IdAndBadgeTypeAndRevokedAtIsNull(
				creator.getId(),
				artist.getId(),
				FanBadgeType.BASIC
		);
		long specialBadgeCount = fbr.countByFan_IdAndArtist_IdAndBadgeTypeAndRevokedAtIsNull(
				creator.getId(),
				artist.getId(),
				FanBadgeType.SPECIAL
		);
		if (basicBadgeCount < MIN_BASIC_BADGE_COUNT ||  specialBadgeCount < MIN_SPECIAL_BADGE_COUNT) {
			throw new IllegalStateException("프로젝트 등록에는 기본 뱃지 5개 이상과 스페셜 뱃지 1개 이상이 필요합니다.");
		}
		LocalDateTime emailVerifiedAt = evs.consumeProjectVerification(
				creator.getId(),
				dto.getEmailVerificationKey()
		);
		
		// 4. Project 저장
		// 화면에서는 날짜(년월일)만 받으므로 여기서 시각을 붙인다.
		// 시작일은 그날 00:00:00부터, 마감일은 그날 23:59:59까지 모금하는 것으로 본다.
		// LocalTime.MAX(23:59:59.999999999)를 쓰면 안 됨 - MySQL DATETIME(6)은 마이크로초까지라
		// 나노초가 반올림되면서 다음 날 00:00:00으로 넘어가 버린다(9/30 입력 -> 10/1 저장).
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
		
		// 7. 생성된 프로젝트 ID 반환
		return savedProject.getId();
	}
}
