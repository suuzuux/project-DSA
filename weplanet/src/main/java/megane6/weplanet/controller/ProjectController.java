package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ProjectDetailView;
import megane6.weplanet.domain.dto.ProjectEligibilityView;
import megane6.weplanet.domain.dto.ProjectRequestDTO;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.FanProjectEventType;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.MembershipService;
import megane6.weplanet.service.ProjectService;
import megane6.weplanet.service.calendar.ArtistAttendanceService;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.community.CommunityDrawerHelper;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/community/{artistId}/project")
public class ProjectController {
	private final ProjectService ps;
	private final UserRepository ur;
	private final CommunityJoinService cjs;
	private final MembershipService ms;
	private final ArtistAttendanceService artistAttendanceService;
	private final CommunityDrawerHelper communityDrawerHelper;
	private final PortalManagementService portalManagementService;
	// flash 문구와 등록 실패 사유(예외 메시지 키) 번역용
	private final megane6.weplanet.i18n.Messages messages;
	private final CommunityArtistResolver communityArtistResolver;

	// 프로젝트 목록 및 등록 폼 화면
	@GetMapping
	public String projectPage(
			@PathVariable Long artistId,
			@RequestParam(defaultValue = ProjectService.SORT_DEADLINE) String sort,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model) {
		if (principal == null) {
			return "redirect:/login";
		}

		User currentUser = ur.findById(principal.getId())
				.orElseThrow(() -> new IllegalArgumentException("error.project.memberNotFound"));

		boolean isOwnCommunity = communityArtistResolver.isArtistOf(currentUser, artistId);
		if (isOwnCommunity) {
			return "redirect:/community/" + artistId + "/fan";
		}
		
		if (currentUser.canParticipateInCommunity()
				&& !cjs.isJoined(currentUser, artistId)) {
			
			addMembershipGateModel(artistId, currentUser, model);
			return "community/membership-required";
		}
		
		ProjectRequestDTO dto = new ProjectRequestDTO();
		dto.setArtistId(artistId);

		model.addAttribute("projectRequestDTO", dto);
		addPageModel(artistId, model, sort, principal);

		return "community/project";
	}
	
	@GetMapping("/eligibility")
	@ResponseBody
	public ProjectEligibilityView projectEligibility(
			@PathVariable Long artistId,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		if (principal == null) {
			throw new IllegalStateException("common.error.loginRequired");
		}
		
		return ps.checkEligibility(principal.getId(), artistId);
	}
	
	// 프로젝트 상세 화면
	@GetMapping("/{projectId}")
	public String projectDetail(
			@PathVariable Long artistId,
			@PathVariable Long projectId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model) {
		if (principal == null) {
			return "redirect:/login";
		}
		
		User currentUser = ur.findById(principal.getId())
				.orElseThrow(() ->
						new IllegalArgumentException("error.project.memberNotFound"));

		boolean isOwnCommunity = currentUser.getId().equals(artistId);
		if (isOwnCommunity && currentUser.getRole() == Role.ARTIST) {
			return "redirect:/community/" + artistId + "/fan";
		}
		
		if ((currentUser.getRole() == Role.FAN || currentUser.getRole() == Role.ARTIST)
				&& !cjs.isJoined(currentUser, artistId)) {
			
			addMembershipGateModel(artistId, currentUser, model);
			return "community/membership-required";
		}
		
		User artist = ur.findById(artistId).filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));

		List<ArtistCardView> artists = portalManagementService.toArtistCards(ur.findByRole(Role.ARTIST));

		model.addAttribute("artist", portalManagementService.toArtistCard(artist));
		model.addAttribute("artists", artists);
		addSidebarModel(currentUser, artist, artists, model);
		model.addAttribute("project", ps.getProjectDetail(projectId, artist, principal));

		return "community/project-detail";
	}

	// 프로젝트 카드 클릭 시 상세 모달에 전달할 데이터
	@GetMapping("/{projectId}/modal")
	@ResponseBody
	public ProjectDetailView projectDetailModal(
			@PathVariable Long artistId,
			@PathVariable Long projectId,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		User artist = ur.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() ->
						new IllegalArgumentException("error.community.artistNotFound")
				);

		return ps.getProjectDetail(projectId, artist, principal);
	}

	// 프로젝트 등록 처리
	@PostMapping
	public String createProject(
			@PathVariable Long artistId,
			@Valid
			@ModelAttribute("projectRequestDTO")
			ProjectRequestDTO dto,
			BindingResult bindingResult,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model,
			RedirectAttributes redirectAttributes) {
		// 로그인하지 않은 경우
		if (principal == null) return "redirect:/login";
		
		// DTO 검증 실패
		if (bindingResult.hasErrors()) {
			addPageModel(artistId, model, ProjectService.SORT_DEADLINE, principal);
			return "community/project";
		}
		
		try {
			// hidden input 이 조작돼도 URL 의 아티스트를 등록 대상으로 쓴다.
			dto.setArtistId(artistId);
			Long projectId = ps.createProject(principal.getId(), dto);
			log.info("팬 프로젝트 등록 완료: projectId={}, creatorId={}", projectId, principal.getId());
			redirectAttributes.addFlashAttribute("successMessage", messages.get("community.project.created"));
			return "redirect:/community/" + artistId + "/project";
		} catch (IllegalArgumentException | IllegalStateException e) {
			// 번역한 예외 문구를 기본 메시지로 넘긴다.
			bindingResult.reject("projectCreateFailed", messages.resolve(e));
			addPageModel(artistId, model, ProjectService.SORT_DEADLINE, principal);
			return "community/project";
		}
	}

	// ADMIN 프로젝트 승인
	@PostMapping("/{projectId}/approve")
	public String approveProject(
			@PathVariable Long artistId,
			@PathVariable Long projectId,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes) {
		if (principal == null) {
			throw new IllegalStateException("error.project.adminLoginRequired");
		}

		ps.approveProject(
				projectId,
				artistId,
				principal.getId(),
				request.getRemoteAddr());
		redirectAttributes.addFlashAttribute("successMessage", messages.get("community.project.approved"));
		return "redirect:/community/" + artistId + "/project/" + projectId;
	}

	// ADMIN 프로젝트 반려
	@PostMapping("/{projectId}/reject")
	public String rejectProject(
			@PathVariable Long artistId,
			@PathVariable Long projectId,
			@RequestParam String rejectionReason,
			HttpServletRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes) {
		if (principal == null) {
			throw new IllegalStateException("error.project.adminLoginRequired");
		}

		ps.rejectProject(
				projectId,
				artistId,
				principal.getId(),
				rejectionReason,
				request.getRemoteAddr());
		redirectAttributes.addFlashAttribute("successMessage", messages.get("community.project.rejected"));
		return "redirect:/community/" + artistId + "/project/" + projectId;
	}
	
	// 커뮤니티 화면과 등록 폼 공통 모델 구성.
	private void addPageModel(Long artistId, Model model, String sort, AuthenticatedUser viewer) {
		User artist = ur.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
		
		User currentUser = ur.findById(viewer.getId()).orElseThrow(() ->
				new IllegalArgumentException("error.project.memberNotFound"));

		if (currentUser.canParticipateInCommunity()) {
			model.addAttribute("registeredEmail", currentUser.getEmail());
			model.addAttribute("accountHolderName", currentUser.getRealName());
		}

		List<ArtistCardView> artists = portalManagementService.toArtistCards(ur.findByRole(Role.ARTIST));
		addSidebarModel(currentUser, artist, artists, model);

		model.addAttribute("artist", portalManagementService.toArtistCard(artist));
		model.addAttribute("artists", artists);
		model.addAttribute("showProjectWriteButton", true);
		model.addAttribute("eventTypes", FanProjectEventType.values());
		model.addAttribute("settlementBanks", SettlementBank.values());

		// 등록 실패로 폼을 다시 그릴 때도 목록을 함께 담는다.
		model.addAttribute("projects", ps.getProjectCards(artist, sort, viewer));
		model.addAttribute("sort", sort);
	}
	
	private void addMembershipGateModel(
			Long artistId,
			User currentUser,
			Model model
	) {
		User artist = ur.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
		
		List<ArtistCardView> artists = portalManagementService.toArtistCards(ur.findByRole(Role.ARTIST));
		
		model.addAttribute("artist", portalManagementService.toArtistCard(artist));
		model.addAttribute("artists", artists);
		addSidebarModel(currentUser, artist, artists, model);
		model.addAttribute("gatedTab", "fan");
	}

	private void addSidebarModel(
			User currentUser,
			User artist,
			List<ArtistCardView> artists,
			Model model
	) {
		boolean isOwnCommunity = communityArtistResolver.isArtistOf(currentUser, artist.getId());
		model.addAttribute("isOwnCommunity", isOwnCommunity);

		Map<Long, CommunityMember> joinedProfiles = cjs.joinedProfilesByArtistId(currentUser);
		Set<Long> joinedArtistIds = cjs.joinedArtistIds(currentUser);

		List<ArtistCardView> joinedArtists = communityDrawerHelper.joined(currentUser, artists, joinedArtistIds);
		List<ArtistCardView> otherCommunities = communityDrawerHelper.otherCommunities(currentUser, artists);

		model.addAttribute("joinedArtists", joinedArtists);
		model.addAttribute("otherCommunities", otherCommunities);
		model.addAttribute("communityJoined", isOwnCommunity || joinedArtistIds.contains(artist.getId()));
		model.addAttribute("myCommunityProfile", joinedProfiles.get(artist.getId()));
		model.addAttribute("membershipActive", false);
		model.addAttribute("artistAttendance", artistAttendanceService.getAllPawColors(artist));
		
		if (currentUser.canParticipateInCommunity()) {
			ms.getMembership(currentUser, artist).ifPresent(membership -> {
				model.addAttribute("membershipActive", !membership.isExpired());
				model.addAttribute("membershipExpiresAt", membership.getExpiresAt());
			});
		}
	}
}
