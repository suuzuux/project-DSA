package megane6.weplanet.service;

import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.util.NicknameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceSignupTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final UserService service = new UserService(userRepository, mock(PasswordEncoder.class),
			mock(NicknameGenerator.class), mock(ApplicationEventPublisher.class),
			mock(CommunityJoinService.class), mock(UserFollowRepository.class));

	// 회원가입 예외도 메시지 키로 던진다
	@Test
	void signupThrowsMessageKeys() {
		SignupRequestDto mismatch = signupRequest("abcd1234", "abcd9999");
		assertEquals("signup.error.passwordMismatch",
				assertThrows(IllegalArgumentException.class, () -> service.signup(mismatch)).getMessage());

		when(userRepository.existsByUsername("newfan01")).thenReturn(true);
		SignupRequestDto taken = signupRequest("abcd1234", "abcd1234");
		assertEquals("signup.error.usernameTaken",
				assertThrows(IllegalArgumentException.class, () -> service.signup(taken)).getMessage());
	}

	// 로그인 정보 객체를 로그에 찍어도 비밀번호 해시는 나오지 않는다
	@Test
	void authenticatedUserToStringHidesPassword() {
		AuthenticatedUser principal = AuthenticatedUser.builder()
				.id(1L).username("newfan01").password("$2a$10$secret-hash").nickname("닉네임").roleName("ROLE_FAN").build();

		assertFalse(principal.toString().contains("secret-hash"));
	}

	private static SignupRequestDto signupRequest(String password, String passwordConfirm) {
		SignupRequestDto dto = new SignupRequestDto();
		dto.setUsername("newfan01");
		dto.setPassword(password);
		dto.setPasswordConfirm(passwordConfirm);
		dto.setRealName("이름");
		dto.setEmail("newfan01@weplanet.test");
		return dto;
	}
}
