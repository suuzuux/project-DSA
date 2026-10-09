package megane6.weplanet.service;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.event.AccountMailEvent;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.PendingSocialSignup;
import megane6.weplanet.util.NicknameGenerator;
import megane6.weplanet.util.UsernameGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialSignupServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final NicknameGenerator nicknameGenerator = mock(NicknameGenerator.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final SocialSignupService service = new SocialSignupService(
			userRepository, mock(UsernameGenerator.class), nicknameGenerator, eventPublisher);

	@BeforeEach
	void setUp() {
		when(userRepository.findByProviderAndProviderId(any(), any())).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(nicknameGenerator.generate()).thenReturn("빛나는여우135");
	}

	// 소셜 이름이 닉네임 규칙(2~15자)을 넘으면 그대로 쓰지 않고 자동 생성 닉네임을 쓴다
	@Test
	void longSocialNameFallsBackToGeneratedNickname() {
		User saved = service.signup(pending("Gildong Hong Smith"), false);

		assertEquals("빛나는여우135", saved.getNickname());
	}

	// 규칙에 맞는 이름은 그대로 닉네임으로 쓴다 (앞뒤 공백은 정리)
	@Test
	void validSocialNameIsKept() {
		User saved = service.signup(pending(" 홍길동 "), false);

		assertEquals("홍길동", saved.getNickname());
	}

	// 가입 완료 메일은 직접 보내지 않고, 가입이 확정된 뒤 보내도록 이벤트만 남긴다 (AccountMailListener)
	@Test
	void signupRequestsWelcomeMailInsteadOfSendingIt() {
		service.signup(pending("홍길동"), true);

		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(event.capture());
		AccountMailEvent mail = (AccountMailEvent) event.getValue();
		assertEquals(AccountMailEvent.Kind.SIGNUP_WELCOME, mail.kind());
		assertEquals(true, mail.marketingConsentGiven());
	}

	// 이미 다른 계정이 쓰는 이메일이면 메시지 키로 막는다 (한국어 문장을 직접 넣지 않음)
	@Test
	void takenEmailThrowsMessageKey() {
		when(userRepository.existsByEmail("hong@gmail.com")).thenReturn(true);

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.signup(pending("홍길동"), false));
		assertEquals("loginEntry.socialEmailTaken", e.getMessage());
	}

	private static PendingSocialSignup pending(String socialName) {
		return PendingSocialSignup.of(AuthProvider.GOOGLE, "google-sub-1", "hong@gmail.com", socialName, socialName);
	}
}
