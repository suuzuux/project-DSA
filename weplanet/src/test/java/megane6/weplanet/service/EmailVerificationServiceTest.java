package megane6.weplanet.service;

import megane6.weplanet.service.account.EmailVerificationService;

import megane6.weplanet.domain.entity.EmailVerification;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.EmailVerificationPurpose;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.account.EmailVerificationRepository;
import megane6.weplanet.repository.main.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {

	private final EmailVerificationRepository emailVerificationRepository =
			mock(EmailVerificationRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final EmailVerificationService service = new EmailVerificationService(
			emailVerificationRepository,
			userRepository,
			passwordEncoder
	);

	@Test
	void issuingNewAdminCodeInvalidatesPreviousUnusedCodes() {
		User admin = mock(User.class);
		EmailVerification previous = mock(EmailVerification.class);
		when(admin.getRole()).thenReturn(Role.ADMIN);
		when(admin.getEmail()).thenReturn("admin@example.com");
		when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
		when(emailVerificationRepository.findByUser_IdAndPurposeAndConsumedAtIsNull(
				1L,
				EmailVerificationPurpose.ADMIN_LOGIN
		)).thenReturn(List.of(previous));
		when(passwordEncoder.encode(anyString())).thenReturn("encoded-code");
		when(emailVerificationRepository.save(any(EmailVerification.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		EmailVerificationService.IssuedVerification issued =
				service.issueAdminLoginVerification(1L);

		verify(previous).invalidate(any(LocalDateTime.class));
		assertNotNull(issued.verificationKey());
		assertEquals("admin@example.com", issued.recipientEmail());
		assertNotNull(issued.rawCode());
		assertNotNull(issued.expiresAt());
	}
}
