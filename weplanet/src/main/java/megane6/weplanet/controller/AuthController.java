package megane6.weplanet.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import megane6.weplanet.service.UserService;
import megane6.weplanet.util.NicknameGenerator;
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
	private final NicknameGenerator nicknameGenerator;
	
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
	
	// 회원가입 방법 선택 화면 (Google/Kakao/LINE/아이디 중 선택하는 목업 화면)
	@GetMapping("/signup")
	public String signupEntry() {
		return "signup-wireframe";
	}
	
	@GetMapping("/signup/id")
	public String signupForm(Model model) {
		SignupRequestDto dto = new SignupRequestDto();
		// 닉네임 칸을 비워두면 화면에 보여준 것과 다른, 서버가 새로 뽑은 닉네임으로 가입되던 문제 수정.
		// 처음부터 실제로 저장될 닉네임을 미리 뽑아서 입력값으로 채워두면, 사용자가 안 건드리고 그대로
		// 제출해도(=resolveNickname에서 "직접 입력한 닉네임"으로 처리됨) 화면에서 본 것과 똑같이 저장된다.
		dto.setNickname(nicknameGenerator.generate());
		model.addAttribute("signupRequestDto", dto);
		return "signup-id";
	}
	
	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute SignupRequestDto signupRequestDto,
						 BindingResult bindingResult,
						 Model model) {
		if (bindingResult.hasErrors()) {
			fillNicknameIfBlank(signupRequestDto);
			return "signup-id";
		}
		// 화면(JS)에서 인증코드 확인을 막아두지만, 직접 POST를 보내는 우회를 막기 위해 서버에서도 확인한다
		if (!emailVerificationService.isVerified(signupRequestDto.getEmail())) {
			model.addAttribute("errorMessage", "이메일 인증을 먼저 완료해주세요.");
			fillNicknameIfBlank(signupRequestDto);
			return "signup-id";
		}
		try {
			userService.signup(signupRequestDto);
			emailVerificationService.clear(signupRequestDto.getEmail());
		} catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", e.getMessage());
			fillNicknameIfBlank(signupRequestDto);
			return "signup-id";
		}
		return "redirect:/login";
	}

	// 다른 항목(비밀번호 등) 검증에 실패해서 회원가입 화면을 다시 보여줄 때, 닉네임 칸을 비워둔 채 왔으면
	// 다시 하나 뽑아 채워준다. 사용자가 직접 입력한 닉네임은 그대로 두고 건드리지 않는다.
	private void fillNicknameIfBlank(SignupRequestDto dto) {
		if (dto.getNickname() == null || dto.getNickname().isBlank()) {
			dto.setNickname(nicknameGenerator.generate());
		}
	}
	
	// 로그인 방법 선택 화면 (Google/Kakao/LINE/아이디 중 선택하는 목업 화면)
	@GetMapping("/login")
	public String loginEntry() {
		return "login-wireframe";
	}
	
	@GetMapping("/login/id")
	public String loginForm() {
		return "login-id";
	}
}