package megane6.weplanet.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import megane6.weplanet.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {
	
	private final UserService userService;
	private final SignupEmailVerificationService emailVerificationService;
	
	// 회원가입 화면의 "중복 확인" 버튼 - 실제로 DB를 조회해서 사용 가능 여부를 JSON으로 알려준다.
	@PostMapping("/signup/username/check")
	@ResponseBody
	public Map<String, Object> checkUsername(@RequestParam String username) {
		Map<String, Object> result = new HashMap<>();
		String trimmed = username == null ? "" : username.trim();
		if (!trimmed.matches("^[a-zA-Z0-9]{4,20}$")) {
			result.put("available", false);
			result.put("message", "아이디는 영문/숫자 4~20자로 입력해주세요.");
			return result;
		}
		boolean available = userService.isUsernameAvailable(trimmed);
		result.put("available", available);
		result.put("message", available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다.");
		return result;
	}
	
	@GetMapping("/signup")
	public String signupForm(Model model) {
		model.addAttribute("signupRequestDto", new SignupRequestDto());
		return "signup-id";
	}
	
	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute SignupRequestDto signupRequestDto,
						 BindingResult bindingResult,
						 Model model) {
		if (bindingResult.hasErrors()) {
			return "signup-id";
		}
		// 화면(JS)에서 인증코드 확인을 막아두지만, 직접 POST를 보내는 우회를 막기 위해 서버에서도 확인한다
		if (!emailVerificationService.isVerified(signupRequestDto.getEmail())) {
			model.addAttribute("errorMessage", "이메일 인증을 먼저 완료해주세요.");
			return "signup-id";
		}
		try {
			userService.signup(signupRequestDto);
			emailVerificationService.clear(signupRequestDto.getEmail());
		} catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", e.getMessage());
			return "signup-id";
		}
		return "redirect:/login";
	}
	
	@GetMapping("/login")
	public String loginForm() {
		return "login-id";
	}
}