package megane6.weplanet.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.RoleHomeRedirects;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import megane6.weplanet.service.email.VerificationPurpose;
import megane6.weplanet.service.UserService;
import megane6.weplanet.util.NicknameGenerator;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
	
	// 아이디 로그인에서 가입된 아이디가 없을 때, [회원가입하기]로 넘어가면 가입 화면에 채워줄 아이디 (SecurityConfig 로그인 실패 처리)
	public static final String SESSION_KEY_LOGIN_NOT_FOUND_USERNAME = "LOGIN_NOT_FOUND_USERNAME";
	// 가입된 아이디가 없어서 로그인에 실패한 횟수 - 5회째에 "회원가입하시겠습니까?" 확인창을 띄운다 (SecurityConfig)
	public static final String SESSION_KEY_LOGIN_NOT_FOUND_COUNT = "LOGIN_NOT_FOUND_COUNT";
	public static final int LOGIN_NOT_FOUND_ASK_AT = 5;

	private final UserService userService;
	private final SignupEmailVerificationService emailVerificationService;
	private final NicknameGenerator nicknameGenerator;
	private final MessageSource messageSource;
	private final megane6.weplanet.i18n.Messages messages;

	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	// 회원가입 화면의 "중복 확인" 버튼 - 실제로 DB를 조회해서 사용 가능 여부를 JSON으로 알려준다.
	@PostMapping("/signup/username/check")
	@ResponseBody
	public Map<String, Object> checkUsername(@RequestParam String username) {
		Map<String, Object> result = new HashMap<>();
		String trimmed = username == null ? "" : username.trim();
		if (!trimmed.matches("^[a-zA-Z0-9]{4,20}$")) {
			result.put("available", false);
			result.put("message", msg("signup.validation.usernamePattern"));
			return result;
		}
		boolean available = userService.isUsernameAvailable(trimmed);
		result.put("available", available);
		result.put("message", available ? msg("signup.usernameCheck.available") : msg("signup.error.usernameTaken"));
		return result;
	}
	
	// 회원가입 방법 선택 화면 (Google/Kakao/LINE/아이디 중 선택하는 목업 화면)
	@GetMapping("/signup")
	public String signupEntry() {
		return "signup-wireframe";
	}
	
	@GetMapping("/signup/id")
	public String signupForm(Model model, HttpSession session) {
		SignupRequestDto dto = new SignupRequestDto();
		// 아이디 로그인에서 "가입된 아이디가 없습니다 → 회원가입하기"로 넘어온 경우, 입력했던 아이디를 채워준다 (한 번만)
		Object notFoundUsername = session.getAttribute(SESSION_KEY_LOGIN_NOT_FOUND_USERNAME);
		if (notFoundUsername instanceof String username) {
			session.removeAttribute(SESSION_KEY_LOGIN_NOT_FOUND_USERNAME);
			if (username.matches("^[a-zA-Z0-9]{4,20}$")) {
				dto.setUsername(username);
			}
		}
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
						 Model model,
						 HttpSession session) {
		if (bindingResult.hasErrors()) {
			fillNicknameIfBlank(signupRequestDto);
			return "signup-id";
		}
		// 화면(JS)에서 인증코드 확인을 막아두지만, 직접 POST를 보내는 우회를 막기 위해 서버에서도 확인한다
		if (!emailVerificationService.isVerified(session, VerificationPurpose.SIGNUP, signupRequestDto.getEmail())) {
			model.addAttribute("errorMessage", msg("signup.error.emailNotVerified"));
			fillNicknameIfBlank(signupRequestDto);
			return "signup-id";
		}
		try {
			userService.signup(signupRequestDto);
			emailVerificationService.clear(session, VerificationPurpose.SIGNUP, signupRequestDto.getEmail());
		} catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", messages.resolve(e));
			fillNicknameIfBlank(signupRequestDto);
			return "signup-id";
		} catch (DataIntegrityViolationException e) {
			// AUTH-11: 중복 확인(existsBy...)과 저장 사이에 같은 아이디나 이메일로 다른 가입이 먼저 끝난 경우(동시 가입).
			// DB 의 유니크 제약(uk_users_username / uk_users_email)이 두 번째 저장을 막는데, 예전에는 그 오류가
			// 그대로 500 화면으로 나갔다.
			model.addAttribute("errorMessage", msg("signup.error.concurrentSignup"));
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
	public String loginEntry(@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal != null) {
			return RoleHomeRedirects.redirectFor(principal);
		}
		return "login-wireframe";
	}
	
	@GetMapping("/login/id")
	public String loginForm(@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal != null) {
			return RoleHomeRedirects.redirectFor(principal);
		}
		return "login-id";
	}
}
