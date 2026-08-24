package megane6.weplanet.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {
	
	private final UserService userService;
	
	@GetMapping("/signup")
	public String signupForm(Model model) {
		model.addAttribute("signupRequestDto", new SignupRequestDto());
		return "signup";
	}
	
	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute SignupRequestDto signupRequestDto,
						 BindingResult bindingResult,
						 Model model) {
		if (bindingResult.hasErrors()) {
			return "signup";
		}
		try {
			userService.signup(signupRequestDto);
		} catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", e.getMessage());
			return "signup";
		}
		return "redirect:/login";
	}
	
	@GetMapping("/login")
	public String loginForm() {
		return "login";
	}
}