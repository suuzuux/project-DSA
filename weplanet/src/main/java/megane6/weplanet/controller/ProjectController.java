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
	private final UserRepository userRepository;
	
	// 프로젝트 목록 및 등록 폼 화면
	@GetMapping
	public String projectPage(@PathVariable Long artistId, Model model) {
		ProjectRequestDTO dto = new ProjectRequestDTO();
		dto.setArtistId(artistId);
		
		model.addAttribute("projectRequestDTO", dto);
		addPageModel(artistId, model);
		
		return "community/project";
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
			addPageModel(artistId, model);
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
			addPageModel(artistId, model);
			return "community/project";
		}
	}
	
	// 팀 공통 커뮤니티 화면과 등록 폼에서 사용할 모델을 함께 구성한다.
	private void addPageModel(Long artistId, Model model) {
		User artist = userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));

		List<ArtistCardView> artists = userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();

		model.addAttribute("artist", ArtistCardView.from(artist));
		model.addAttribute("artists", artists);
		model.addAttribute("eventTypes", FanProjectEventType.values());
		model.addAttribute("settlementBanks", SettlementBank.values());
	}
}
