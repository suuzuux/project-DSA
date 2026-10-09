package megane6.weplanet.controller;

import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.UserService;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import megane6.weplanet.service.email.VerificationPurpose;
import megane6.weplanet.util.NicknameGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AuthControllerSignupTest {

	private final UserService userService = mock(UserService.class);
	private final SignupEmailVerificationService emailVerificationService = mock(SignupEmailVerificationService.class);
	private final SocialLoginSessionSupport loginSessionSupport = mock(SocialLoginSessionSupport.class);

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
			userService,
			emailVerificationService,
			mock(NicknameGenerator.class),
			mock(MessageSource.class),
			mock(Messages.class),
			loginSessionSupport))
			// 실제 앱처럼 @AuthenticationPrincipal 에 로그인한 회원을 넣어 준다 (로그인 안 했으면 null)
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.build();

	@AfterEach
	void logout() {
		SecurityContextHolder.clearContext();
	}

	// 로그인한 채로 가입 화면 주소를 열면 로그인 화면(/login)처럼 역할별 첫 화면으로 보낸다
	@Test
	void loggedInUserIsSentToRoleHomeFromSignupPages() throws Exception {
		loginAs("ROLE_FAN");
		mockMvc.perform(get("/signup")).andExpect(redirectedUrl("/"));
		mockMvc.perform(get("/signup/id")).andExpect(redirectedUrl("/"));

		loginAs("ROLE_AGENCY");
		mockMvc.perform(get("/signup/id")).andExpect(redirectedUrl("/portal/dashboard"));
	}

	// 로그인하지 않았으면 가입 화면이 그대로 열린다
	@Test
	void guestSeesSignupPages() throws Exception {
		mockMvc.perform(get("/signup")).andExpect(view().name("signup-wireframe"));
		mockMvc.perform(get("/signup/id")).andExpect(view().name("signup-id"));
	}

	// 아이디 회원가입이 끝나면 로그인 화면을 거치지 않고 바로 로그인된 채로 메인으로 간다
	@Test
	void signupLogsInAndRedirectsToMain() throws Exception {
		User saved = User.createFan("newfan01", "encoded", "이름", "닉네임", "newfan01@weplanet.test");
		when(emailVerificationService.isVerified(any(), eq(VerificationPurpose.SIGNUP), eq("newfan01@weplanet.test")))
				.thenReturn(true);
		when(userService.signup(any(SignupRequestDto.class))).thenReturn(saved);

		mockMvc.perform(validSignup())
				.andExpect(redirectedUrl("/"))
				.andExpect(flash().attribute(AuthController.FLASH_SIGNUP_WELCOME, true));

		verify(loginSessionSupport).loginAs(eq(saved), any(), any());
	}

	// 이메일 인증을 안 했으면 가입도, 로그인도 하지 않고 가입 화면에 남는다
	@Test
	void signupWithoutEmailVerificationDoesNotLogIn() throws Exception {
		when(emailVerificationService.isVerified(any(), any(), any())).thenReturn(false);

		mockMvc.perform(validSignup())
				.andExpect(view().name("signup-id"));

		verify(userService, never()).signup(any());
		verify(loginSessionSupport, never()).loginAs(any(), any(), any());
	}

	// 닉네임 중복처럼 서버에서 막혀 가입 화면이 다시 열려도, 이미 마친 아이디 중복 확인·이메일 인증은 이어간다
	@Test
	void formShownAgainKeepsCheckedUsernameAndEmailVerification() throws Exception {
		when(emailVerificationService.isVerified(any(), eq(VerificationPurpose.SIGNUP), eq("newfan01@weplanet.test")))
				.thenReturn(true);
		when(userService.signup(any(SignupRequestDto.class)))
				.thenThrow(new IllegalArgumentException("signup.error.nicknameTaken"));
		when(userService.isUsernameAvailable("newfan01")).thenReturn(true);

		mockMvc.perform(validSignup())
				.andExpect(view().name("signup-id"))
				.andExpect(model().attribute("checkedUsername", "newfan01"))
				.andExpect(model().attribute("emailVerified", true));

		verify(loginSessionSupport, never()).loginAs(any(), any(), any());
	}

	// 그 사이에 다른 사람이 같은 아이디로 가입했으면 중복 확인은 다시 해야 한다
	@Test
	void formShownAgainAsksUsernameCheckWhenTakenMeanwhile() throws Exception {
		when(emailVerificationService.isVerified(any(), any(), any())).thenReturn(true);
		when(userService.signup(any(SignupRequestDto.class)))
				.thenThrow(new IllegalArgumentException("signup.error.usernameTaken"));
		when(userService.isUsernameAvailable("newfan01")).thenReturn(false);

		mockMvc.perform(validSignup())
				.andExpect(model().attribute("checkedUsername", ""));
	}

	private static void loginAs(String roleName) {
		AuthenticatedUser user = AuthenticatedUser.builder()
				.id(7L).username("hong01").nickname("홍길동").roleName(roleName).enabled(true).build();
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
	}

	private static org.springframework.test.web.servlet.RequestBuilder validSignup() {
		return post("/signup")
				.param("username", "newfan01")
				.param("password", "abcd123!")
				.param("passwordConfirm", "abcd123!")
				.param("realName", "이름")
				.param("nickname", "닉네임")
				.param("email", "newfan01@weplanet.test");
	}
}
