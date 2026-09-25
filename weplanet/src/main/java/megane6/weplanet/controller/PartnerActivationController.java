package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.service.AgencyActivationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/*
	입점 승인 메일의 활성화 링크를 받아 비밀번호를 설정하는 화면.
	아직 로그인할 수 없는 사용자가 들어오므로 SecurityConfig에서 공개 url로 열어둠
 */
@Controller
@RequestMapping("/partner/activate")
@RequiredArgsConstructor
public class PartnerActivationController {
	
	private final AgencyActivationService aas;
	
	@GetMapping
	public String activationForm(@RequestParam(required = false) String key,
								 @RequestParam(required = false) String token,
								 Model model) {
		try {
			AgencyActivationService.ActivationTarget target
					= aas.loadActivationTarget(key, token);
			
			model.addAttribute("target", target);
			model.addAttribute("key", key);
			model.addAttribute("token", token);
		} catch (IllegalArgumentException | IllegalStateException e) {
			// 링크 자체가 잘못됐거나 만료됐으면 폼 없이 안내 문구만 보여준다.
			model.addAttribute("linkError", e.getMessage());
		}
		
		return "partner-activate";
	}
	
	@PostMapping
	public String activate(@RequestParam String key,
						   @RequestParam String token,
						   @RequestParam String newPassword,
						   @RequestParam String confirmPassword,
						   Model model) {
		try {
			aas.activate(key, token, newPassword, confirmPassword);
		} catch (IllegalArgumentException | IllegalStateException e) {
			// 비밀번호 형식 오류 같은 경우 다시 입력할 수 있도록 같은 화면을 돌려준다
			// redirect로 돌리면 토큰을 다시 URL에 붙여야 해서, 여기서는 바로 화면을 그린다.
			model.addAttribute("formError", e.getMessage());
			
			try {
				model.addAttribute("target", aas.loadActivationTarget(key, token));
				model.addAttribute("key", key);
				model.addAttribute("token", token);
			} catch (IllegalArgumentException | IllegalStateException linkException) {
				model.addAttribute("linkError", linkException.getMessage());
			}
			
			return "partner-activate";
		}
		
		return "redirect:/portal/login?activated&role=AGENCY";
	}
}
