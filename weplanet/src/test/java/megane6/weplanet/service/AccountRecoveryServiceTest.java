package megane6.weplanet.service;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountRecoveryServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final AccountRecoveryService service =
			new AccountRecoveryService(userRepository, mock(PasswordEncoder.class));

	// 가입 때 Kwon@Gmail.com 으로 쓴 사람이 비밀번호 찾기에서 kwon@gmail.com 으로 입력해도 같은 계정으로 본다
	// (예전에는 equals 로 비교해서 "일치하면 보냈습니다"만 뜨고 메일이 오지 않았다)
	@Test
	void resetPasswordMatchesEmailIgnoringCase() {
		User user = User.createFan("kwon01", "encoded", "권형준", "닉네임", "Kwon@Gmail.com");
		when(userRepository.findByUsername("kwon01")).thenReturn(Optional.of(user));

		assertTrue(service.matchesUsernameAndEmail("kwon01", "kwon@gmail.com"));
		// 코드는 입력한 주소가 아니라 가입 때 등록한 주소로 보낸다
		assertEquals(Optional.of("Kwon@Gmail.com"), service.resetPasswordRecipient("kwon01", " kwon@gmail.com "));
	}

	@Test
	void resetPasswordRejectsDifferentEmail() {
		User user = User.createFan("kwon01", "encoded", "권형준", "닉네임", "kwon@gmail.com");
		when(userRepository.findByUsername("kwon01")).thenReturn(Optional.of(user));

		assertFalse(service.matchesUsernameAndEmail("kwon01", "other@gmail.com"));
		assertTrue(service.resetPasswordRecipient("kwon01", "other@gmail.com").isEmpty());
	}

	// 아이디 찾기도 등록된 주소로 보낸다 (DB 조회는 대소문자를 무시한다)
	@Test
	void findIdSendsToRegisteredAddress() {
		User user = User.createFan("kwon01", "encoded", "권형준", "닉네임", "Kwon@Gmail.com");
		when(userRepository.findByEmail("kwon@gmail.com")).thenReturn(Optional.of(user));

		assertEquals(Optional.of("Kwon@Gmail.com"), service.findIdRecipient("권형준", "kwon@gmail.com"));
		assertTrue(service.findIdRecipient("다른이름", "kwon@gmail.com").isEmpty());
	}

	// 카카오 가입자가 비밀번호를 등록했어도 이메일이 받을 수 없는 시스템 주소 그대로면 코드를 보내지 않는다
	// (User.hasPlaceholderEmail() 기준 - 연동을 해제해 provider 가 비어 있어도 같다)
	@Test
	void placeholderEmailIsNotRecoverableEvenWithPassword() {
		User kakao = User.createSocialFan("kakao123456", "encoded", "권형준", "닉네임", "kakao_7@kakao.weplanet.local",
				AuthProvider.KAKAO, "7");
		kakao.unlinkSocialProvider();
		when(userRepository.findByUsername("kakao123456")).thenReturn(Optional.of(kakao));

		assertTrue(service.resetPasswordRecipient("kakao123456", "kakao_7@kakao.weplanet.local").isEmpty());
	}

	// 예외 메시지는 다른 서비스와 같이 메시지 키로 던진다 (컨트롤러가 Messages.resolve 로 번역)
	@Test
	void resetPasswordThrowsMessageKeys() {
		User user = User.createFan("kwon01", "encoded", "권형준", "닉네임", "kwon@gmail.com");
		when(userRepository.findByUsername("kwon01")).thenReturn(Optional.of(user));

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.resetPassword("kwon01", "kwon@gmail.com", "abcd1234", "abcd9999"));
		assertEquals("resetPassword.confirmMismatch", e.getMessage());
	}

	// 비밀번호가 없는 소셜 전용 계정은 찾기 대상이 아니라서 코드를 보내지 않는다
	@Test
	void socialOnlyAccountGetsNoCode() {
		User social = User.createSocialFan("google123456", null, "권형준", "닉네임", "kwon@gmail.com",
				AuthProvider.GOOGLE, "google-sub");
		when(userRepository.findByEmail("kwon@gmail.com")).thenReturn(Optional.of(social));

		assertTrue(service.findIdRecipient("권형준", "kwon@gmail.com").isEmpty());
	}
}
