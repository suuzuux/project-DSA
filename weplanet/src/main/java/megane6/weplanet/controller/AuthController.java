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
	
	// 아이디 로그인에서 가입된 아이디가 없을 때, [회원가입하기]로 넘어가면 가입 화면에 채워줄 아이디 (SecurityConfig 로그인 실패 처리)
	public static final String SESSION_KEY_LOGIN_NOT_FOUND_USERNAME = "LOGIN_NOT_FOUND_USERNAME";
	// 가입된 아이디가 없어서 로그인에 실패한 횟수 - 5회째에 "회원가입하시겠습니까?" 확인창을 띄운다 (SecurityConfig)
	public static final String SESSION_KEY_LOGIN_NOT_FOUND_COUNT = "LOGIN_NOT_FOUND_COUNT";
	public static final int LOGIN_NOT_FOUND_ASK_AT = 5;
	// 회원가입 직후 자동 로그인으로 메인에 들어왔을 때 환영 토스트를 한 번 띄우는 표시 (index.html)
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

	// 회원가입 화면의 "중복 확인" 버튼 - 실제로 DB를 조회해서 사용 가능 여부를 JSON으로 알려준다.
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
		// 아이디 로그인에서 "가입된 아이디가 없습니다 → 회원가입하기"로 넘어온 경우, 입력했던 아이디를 채워준다 (한 번만)
		Object notFoundUsername = session.getAttribute(SESSION_KEY_LOGIN_NOT_FOUND_USERNAME);
		if (notFoundUsername instanceof String username) {
			session.removeAttribute(SESSION_KEY_LOGIN_NOT_FOUND_USERNAME);
			if (username.matches(USERNAME_PATTERN)) {
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
						 HttpSession session,
						 HttpServletRequest request,
						 HttpServletResponse response,
						 RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return showSignupFormAgain(signupRequestDto, session, model);
		}
		// 화면(JS)에서 인증코드 확인을 막아두지만, 직접 POST를 보내는 우회를 막기 위해 서버에서도 확인한다
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
			// AUTH-11: 중복 확인(existsBy...)과 저장 사이에 같은 아이디나 이메일로 다른 가입이 먼저 끝난 경우(동시 가입).
			// DB 의 유니크 제약(uk_users_username / uk_users_email)이 두 번째 저장을 막는데, 예전에는 그 오류가
			// 그대로 500 화면으로 나갔다.
			model.addAttribute("errorMessage", msg("signup.error.concurrentSignup"));
			return showSignupFormAgain(signupRequestDto, session, model);
		}
		// 가입이 끝나면 로그인 화면을 거치지 않고 바로 로그인시켜서 메인으로 보낸다 (소셜 회원가입과 같은 흐름).
		// 이메일 인증을 마쳤고 방금 본인이 정한 비밀번호로 만든 계정이라 다시 입력받을 이유가 없다.
		// 세션 id 교체 · 동시 로그인 제한은 loginAs 가 폼 로그인과 똑같이 처리한다.
		// 마지막 로그인 시각은 UserService.signup 에서, 화면 언어는 가입하던 언어 그대로 이어진다.
		loginSessionSupport.loginAs(user, request, response);
		redirectAttributes.addFlashAttribute(FLASH_SIGNUP_WELCOME, true);
		return "redirect:/";
	}

	// 검증에 실패해서(닉네임 중복 등) 회원가입 화면을 다시 보여줄 때, 이미 마친 확인은 그대로 이어간다.
	// 예전에는 화면 JS 가 항상 "중복 확인 전 / 이메일 미인증"으로 시작해서, 닉네임 하나만 고치려 해도 아이디 중복 확인과
	// 이메일 인증(코드 재발송 - 60초 제한에 걸리기도 함)을 처음부터 다시 해야 했다. 이메일 인증은 서버 세션에 30분 동안 남아 있다.
	//  - checkedUsername: 형식이 맞고 지금도 쓸 수 있는 아이디면 그 값 (중복 확인을 다시 하지 않아도 됨)
	//  - emailVerified: 이 세션에서 그 이메일로 가입 인증을 마쳤는지
	private String showSignupFormAgain(SignupRequestDto dto, HttpSession session, Model model) {
		fillNicknameIfBlank(dto);
		String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
		boolean usernameUsable = username.matches(USERNAME_PATTERN) && userService.isUsernameAvailable(username);
		model.addAttribute("checkedUsername", usernameUsable ? username : "");
		model.addAttribute("emailVerified",
				emailVerificationService.isVerified(session, VerificationPurpose.SIGNUP, dto.getEmail()));
		return "signup-id";
	}

	// 다른 항목(비밀번호 등) 검증에 실패해서 회원가입 화면을 다시 보여줄 때, 닉네임 칸을 비워둔 채 왔으면
	// 다시 하나 뽑아 채워준다. 사용자가 직접 입력한 닉네임은 그대로 두고 건드리지 않는다.
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
