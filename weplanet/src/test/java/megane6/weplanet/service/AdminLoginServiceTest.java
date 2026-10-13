package megane6.weplanet.service;

import megane6.weplanet.service.account.EmailVerificationService;
import megane6.weplanet.service.admin.AdminLoginService;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.email.MailSenderService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminLoginServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
	private final MailSenderService mailSenderService = mock(MailSenderService.class);
	private final Messages messages = mock(Messages.class);
	private final AdminLoginService service = new AdminLoginService(
			userRepository,
			passwordEncoder,
			emailVerificationService,
			mailSenderService,
			messages
	);

	@Test
	void successfulConfirmationConsumesCodeAndRecordsLogin() {
		User admin = admin();
		when(emailVerificationService.confirmAdminLoginVerification(1L, "verification-key", "123456"))
				.thenReturn(new EmailVerificationService.VerificationResult(true, 5));

		User result = service.confirmCode("  admin  ", "password", "verification-key", "123456");

		assertSame(admin, result);
		verify(emailVerificationService).consumeAdminLoginVerification(1L, "verification-key");
		verify(admin).recordLogin();
	}

	@Test
	void invalidCodeDoesNotCreateReusableLogin() {
		User admin = admin();
		when(messages.get("admin.login.error.invalidCode", 4))
				.thenReturn("인증번호가 올바르지 않습니다. (남은 시도 4회)");
		when(emailVerificationService.confirmAdminLoginVerification(1L, "verification-key", "000000"))
				.thenReturn(new EmailVerificationService.VerificationResult(false, 4));

		assertThrows(
				IllegalArgumentException.class,
				() -> service.confirmCode("admin", "password", "verification-key", "000000")
		);

		verify(emailVerificationService, never())
				.consumeAdminLoginVerification(anyLong(), anyString());
		verify(admin, never()).recordLogin();
	}

	private User admin() {
		User admin = mock(User.class);
		when(admin.getId()).thenReturn(1L);
		when(admin.getPassword()).thenReturn("encoded-password");
		when(admin.getRole()).thenReturn(Role.ADMIN);
		when(admin.isLoginable()).thenReturn(true);
		when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
		when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
		return admin;
	}
}
