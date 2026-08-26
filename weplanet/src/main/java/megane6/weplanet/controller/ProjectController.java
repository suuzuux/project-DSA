package megane6.weplanet.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ProjectRequestDTO;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanProjectEventType;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.ProjectService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/community/{artistId}/project")
public class ProjectController {
	private final ProjectService ps;
	private final UserRepository ur;
	
	// 프로젝트 목록 및 등록 폼 화면
	@GetMapping
	public String projectPage(
			@PathVariable Long artistId,
			@RequestParam(defaultValue = ProjectService.SORT_DEADLINE) String sort,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model) {
		ProjectRequestDTO dto = new ProjectRequestDTO();
		dto.setArtistId(artistId);

		model.addAttribute("projectRequestDTO", dto);
		addPageModel(artistId, model, sort, principal);

		return "community/project";
	}
	
	// 프로젝트 상세 화면
	@GetMapping("/{projectId}")
	public String projectDetail(
			@PathVariable Long artistId,
			@PathVariable Long projectId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model) {
		User artist = ur.findById(artistId).filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));

		List<ArtistCardView> artists = ur.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();

		model.addAttribute("artist", ArtistCardView.from(artist));
		model.addAttribute("artists", artists);
		model.addAttribute("project", ps.getProjectDetail(projectId, artist, principal));

		return "community/project-detail";
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
			// hidden input이 조작되더라도 URL의 아티스트를 등록 대상으로 사용한다.
			dto.setArtistId(artistId);
			Long projectId = ps.createProject(principal.getId(), dto);
			log.info("팬 프로젝트 등록 완료: projectId={}, creatorId={}", projectId, principal.getId());
			redirectAttributes.addFlashAttribute("successMessage", "프로젝트 등록 신청이 완료되었습니다.");
			return "redirect:/community/" + artistId + "/project";
		} catch (IllegalArgumentException | IllegalStateException e) {
			bindingResult.reject("projectCreateFailed", e.getMessage());
			addPageModel(artistId, model, ProjectService.SORT_DEADLINE, principal);
			return "community/project";
		}
	}

	// ADMIN 프로젝트 승인
	@PostMapping("/{projectId}/approve")
	public String approveProject(
			@PathVariable Long artistId,
			@PathVariable Long projectId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes) {
		if (principal == null) {
			throw new IllegalStateException("ADMIN 로그인이 필요합니다.");
		}

		ps.approveProject(projectId, artistId, principal.getId());
		redirectAttributes.addFlashAttribute("successMessage", "프로젝트를 승인했습니다.");
		return "redirect:/community/" + artistId + "/project/" + projectId;
	}

	// ADMIN 프로젝트 반려
	@PostMapping("/{projectId}/reject")
	public String rejectProject(
			@PathVariable Long artistId,
			@PathVariable Long projectId,
			@RequestParam String rejectionReason,
			@AuthenticationPrincipal AuthenticatedUser principal,
			RedirectAttributes redirectAttributes) {
		if (principal == null) {
			throw new IllegalStateException("ADMIN 로그인이 필요합니다.");
		}

		ps.rejectProject(projectId, artistId, principal.getId(), rejectionReason);
		redirectAttributes.addFlashAttribute("successMessage", "프로젝트를 반려했습니다.");
		return "redirect:/community/" + artistId + "/project/" + projectId;
	}
	
	// 팀 공통 커뮤니티 화면과 등록 폼에서 사용할 모델을 함께 구성한다.
	private void addPageModel(Long artistId, Model model, String sort, AuthenticatedUser viewer) {
		User artist = ur.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
		
		if (viewer != null && Role.FAN.authority().equals(viewer.getRoleName())) {
			User currentUser = ur.findById(viewer.getId()).orElseThrow(() ->
							new IllegalArgumentException("로그인 회원을 찾을 수 없습니다."));
			
			model.addAttribute("registeredEmail", currentUser.getEmail());
		}

		List<ArtistCardView> artists = ur.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();

		model.addAttribute("artist", ArtistCardView.from(artist));
		model.addAttribute("artists", artists);
		model.addAttribute("eventTypes", FanProjectEventType.values());
		model.addAttribute("settlementBanks", SettlementBank.values());

		// 목록 - 등록 실패로 폼을 다시 그릴 때도 뒤쪽 목록은 그대로 보여야 하므로 여기서 함께 담는다.
		model.addAttribute("projects", ps.getProjectCards(artist, sort, viewer));
		model.addAttribute("sort", sort);
	}
}
