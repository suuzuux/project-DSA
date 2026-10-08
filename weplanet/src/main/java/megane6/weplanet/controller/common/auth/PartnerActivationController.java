package megane6.weplanet.controller.common.auth;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.service.agency.AgencyActivationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 입점 승인 메일의 활성화 링크로 비밀번호를 설정하는 화면 (공개 URL).
@Controller
@RequestMapping("/partner/activate")
@RequiredArgsConstructor
public class PartnerActivationController {
	
	private final AgencyActivationService aas;
	private final Messages messages;
	
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
			model.addAttribute("linkError", messages.resolve(e));
		}
		
		return "common/auth/partner-activate";
	}
	
	@PostMapping
	public String activate(@RequestParam String key,
						   @RequestParam String token,
						   @RequestParam String newPassword,
						   @RequestParam String confirmPassword,
						   Model model) {
		User activated;
		
		try {
			activated = aas.activate(key, token, newPassword, confirmPassword);
		} catch (IllegalArgumentException | IllegalStateException e) {
			// 형식 오류면 토큰을 유지하도록 리다이렉트 없이 같은 화면을 다시 그린다.
			model.addAttribute("formError", messages.resolve(e));
			
			try {
				model.addAttribute("target", aas.loadActivationTarget(key, token));
				model.addAttribute("key", key);
				model.addAttribute("token", token);
			} catch (IllegalArgumentException | IllegalStateException linkException) {
				model.addAttribute("linkError", messages.resolve(linkException));
			}
			
			return "common/auth/partner-activate";
		}
		
		// 활성화한 계정 종류에 맞는 로그인 탭을 열어준다.
		String tab = activated.getRole() == Role.ARTIST ? "ARTIST" : "AGENCY";
		
		return "redirect:/portal/login?activated&role=" + tab;
	}
}
