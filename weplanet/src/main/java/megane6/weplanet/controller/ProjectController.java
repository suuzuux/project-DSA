package megane6.weplanet.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ProjectRequestDTO;
import megane6.weplanet.domain.entity.enumfolder.FanProjectEventType;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.ProjectService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/projects")
public class ProjectController {
	private final ProjectService ps;
	
	// 프로젝트 목록 및 등록 폼 화면
	@GetMapping
	public String projectPage(@RequestParam Long groupId, Model model) {
		ProjectRequestDTO dto = new ProjectRequestDTO();
		dto.setGroupId(groupId);
		
		model.addAttribute("projectRequestDTO", dto);
		addFormOptions(model);
		
		return "community/project";
	}
	
	// 프로젝트 등록 처리
	@PostMapping
	public String createProject(
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
			addFormOptions(model);
			return "community/project";
		}
		
		try {
			Long projectId = ps.createProject(principal.getId(), dto);
			log.info("팬 프로젝트 등록 완료: projectId={}, creatorId={}", projectId, principal.getId());
			redirectAttributes.addFlashAttribute("successMessage", "프로젝트 등록 신청이 완료되었습니다.");
			return "redirect:/projects?groupId=" + dto.getGroupId();
		} catch (IllegalArgumentException | IllegalStateException e) {
			bindingResult.reject("projectCreateFailed", e.getMessage());
			addFormOptions(model);
			return "community/project";
		}
	}
	
	// 등록 폼의 select 태그에서 사용할 선택지
	private void addFormOptions(Model model) {
		model.addAttribute("eventTypes", FanProjectEventType.values());
		model.addAttribute("settlementBanks", SettlementBank.values());
	}
}
