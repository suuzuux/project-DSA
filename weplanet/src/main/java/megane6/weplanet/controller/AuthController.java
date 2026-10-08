package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.RoleHomeRedirects;
import megane6.weplanet.security.SocialLoginSessionSupport;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {
	
	// 없는 아이디로 로그인 실패 시 회원가입 화면에 채워 줄 아이디.
	public static final String SESSION_KEY_LOGIN_NOT_FOUND_USERNAME = "LOGIN_NOT_FOUND_USERNAME";
	// 없는 아이디로 로그인 실패한 횟수 (5회째 회원가입 안내).
	public static final String SESSION_KEY_LOGIN_NOT_FOUND_COUNT = "LOGIN_NOT_FOUND_COUNT";
	public static final int LOGIN_NOT_FOUND_ASK_AT = 5;
	// 회원가입 직후 자동 로그인 시 환영 토스트를 띄우는 표시.
	public static final String FLASH_SIGNUP_WELCOME = "signupWelcome";
	// 아이디 형식 (SignupRequestDto 의 @Pattern 과 같은 규칙)
	private static final String USERNAME_PATTERN = "^[a-zA-Z0-9]{4,20}$";

	private final UserService userService;
	private final SignupEmailVerificationService emailVerificationService;
	private final NicknameGenerator nicknameGenerator;
	private final MessageSource messageSource;
	private final megane6.weplanet.i18n.Messages messages;
	private final SocialLoginSessionSupport loginSessionSupport;

	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	// 아이디 중복 확인 (JSON 응답).
	@PostMapping("/signup/username/check")
	@ResponseBody
	public Map<String, Object> checkUsername(@RequestParam String username) {
		Map<String, Object> result = new HashMap<>();
		String trimmed = username == null ? "" : username.trim();
		if (!trimmed.matches(USERNAME_PATTERN)) {
			result.put("available", false);
			result.put("message", msg("signup.validation.usernamePattern"));
			return result;
		}
		boolean available = userService.isUsernameAvailable(trimmed);
		result.put("available", available);
		result.put("message", available ? msg("signup.usernameCheck.available") : msg("signup.error.usernameTaken"));
		return result;
	}
	
	// 회원가입 방법 선택 화면 (Google/Kakao/LINE/아이디 중 선택)
	@GetMapping("/signup")
	public String signupEntry() {
		return "signup-wireframe";
	}
	
	@GetMapping("/signup/id")
	public String signupForm(Model model, HttpSession session) {
		SignupRequestDto dto = new SignupRequestDto();
		// 로그인 실패 화면에서 넘어온 경우 입력했던 아이디를 한 번 채워 준다.
		Object notFoundUsername = session.getAttribute(SESSION_KEY_LOGIN_NOT_FOUND_USERNAME);
		if (notFoundUsername instanceof String username) {
			session.removeAttribute(SESSION_KEY_LOGIN_NOT_FOUND_USERNAME);
			if (username.matches(USERNAME_PATTERN)) {
				dto.setUsername(username);
			}
		}
		// 실제로 저장될 닉네임을 미리 생성해 입력칸에 채운다.
		dto.setNickname(nicknameGenerator.generate());
		model.addAttribute("signupRequestDto", dto);
		return "signup-id";
	}
	
	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute SignupRequestDto signupRequestDto,
						 BindingResult bindingResult,
						 Model model,
						 HttpSession session,
						 HttpServletRequest request,
						 HttpServletResponse response,
						 RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return showSignupFormAgain(signupRequestDto, session, model);
		}
		// 직접 POST 우회를 막기 위해 서버에서도 인증 여부를 확인한다.
		if (!emailVerificationService.isVerified(session, VerificationPurpose.SIGNUP, signupRequestDto.getEmail())) {
			model.addAttribute("errorMessage", msg("signup.error.emailNotVerified"));
			return showSignupFormAgain(signupRequestDto, session, model);
		}
		User user;
		try {
			user = userService.signup(signupRequestDto);
			emailVerificationService.clear(session, VerificationPurpose.SIGNUP, signupRequestDto.getEmail());
		} catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", messages.resolve(e));
			return showSignupFormAgain(signupRequestDto, session, model);
		} catch (DataIntegrityViolationException e) {
			// 동시 가입으로 유니크 제약에 걸리면 500 대신 안내 문구를 보여준다.
			model.addAttribute("errorMessage", msg("signup.error.concurrentSignup"));
			return showSignupFormAgain(signupRequestDto, session, model);
		}
		// 가입이 끝나면 바로 로그인시켜 메인으로 보낸다.
		loginSessionSupport.loginAs(user, request, response);
		redirectAttributes.addFlashAttribute(FLASH_SIGNUP_WELCOME, true);
		return "redirect:/";
	}

	// 가입 화면을 다시 보여줄 때 이미 마친 중복 확인·이메일 인증 상태를 유지한다.
	private String showSignupFormAgain(SignupRequestDto dto, HttpSession session, Model model) {
		fillNicknameIfBlank(dto);
		String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
		boolean usernameUsable = username.matches(USERNAME_PATTERN) && userService.isUsernameAvailable(username);
		model.addAttribute("checkedUsername", usernameUsable ? username : "");
		model.addAttribute("emailVerified",
				emailVerificationService.isVerified(session, VerificationPurpose.SIGNUP, dto.getEmail()));
		return "signup-id";
	}

	// 닉네임이 비어 있으면 다시 생성해 채운다 (직접 입력한 값은 유지).
	private void fillNicknameIfBlank(SignupRequestDto dto) {
		if (dto.getNickname() == null || dto.getNickname().isBlank()) {
			dto.setNickname(nicknameGenerator.generate());
		}
	}
	
	// 로그인 방법 선택 화면 (Google/Kakao/LINE/아이디 중 선택)
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
