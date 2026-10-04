package megane6.weplanet.service.email;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.event.AccountMailEvent;
import megane6.weplanet.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountMailListenerTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final MarketingConsentEmailService mailService = mock(MarketingConsentEmailService.class);
	private final AccountMailListener listener = new AccountMailListener(userRepository, mailService);

	// 광고·마케팅에 동의하고 가입하면 가입 완료 메일 → 커뮤니티 가입 유도 메일 순서로 보낸다
	@Test
	void signupWithConsentSendsWelcomeThenInvite() {
		User user = User.createFan("kwon01", "encoded", "권형준", "닉네임", "kwon@gmail.com");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		listener.onAccountMail(AccountMailEvent.signupWelcome(1L, true));

		InOrder order = inOrder(mailService);
		order.verify(mailService).sendSignupWelcomeEmail(user, true);
		order.verify(mailService).sendCommunityInviteEmail(user);
	}

	@Test
	void signupWithoutConsentSendsOnlyWelcome() {
		User user = User.createFan("kwon01", "encoded", "권형준", "닉네임", "kwon@gmail.com");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		listener.onAccountMail(AccountMailEvent.signupWelcome(1L, false));

		verify(mailService).sendSignupWelcomeEmail(user, false);
		verify(mailService, never()).sendCommunityInviteEmail(user);
	}

	// 가입 완료 메일이 실패해도 이어지는 메일은 보낸다 (가입에는 영향 없음)
	@Test
	void failedWelcomeStillSendsInvite() {
		User user = User.createFan("kwon01", "encoded", "권형준", "닉네임", "kwon@gmail.com");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		doThrow(new RuntimeException("SMTP down")).when(mailService).sendSignupWelcomeEmail(user, true);

		listener.onAccountMail(AccountMailEvent.signupWelcome(1L, true));

		verify(mailService).sendCommunityInviteEmail(user);
	}

	// 카카오/LINE 가입자의 시스템 주소(*.weplanet.local)는 받을 수 없으므로 보내지 않는다
	@Test
	void placeholderEmailIsSkipped() {
		User kakao = User.createSocialFan("kakao123456", null, "카카오사용자", "닉네임",
				"kakao_1@kakao.weplanet.local", AuthProvider.KAKAO, "1");
		when(userRepository.findById(2L)).thenReturn(Optional.of(kakao));

		listener.onAccountMail(AccountMailEvent.marketingConsentConfirmed(2L));

		verifyNoInteractions(mailService);
	}
}
