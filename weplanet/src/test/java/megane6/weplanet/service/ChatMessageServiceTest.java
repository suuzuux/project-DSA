package megane6.weplanet.service;

import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.ChatMessageRepository;
import megane6.weplanet.repository.MembershipPeriodRepository;
import megane6.weplanet.repository.MembershipRepository;
import megane6.weplanet.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatMessageServiceTest {

	private static final Long FAN = 1L;
	private static final Long ARTIST = 2L;

	private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
	private final MembershipPeriodRepository periodRepository = mock(MembershipPeriodRepository.class);
	private final ChatMessageService service = new ChatMessageService(mock(ChatMessageRepository.class),
			mock(UserRepository.class), membershipRepository, periodRepository);

	private final User fan = user(FAN);
	private final User artist = user(ARTIST);

	// 가입한 적이 없으면 DM은 막히지만, 배너는 "만료"가 아니라 가입 안내여야 한다
	@Test
	void neverJoinedFanIsBlockedButNotShownAsExpired() {
		when(membershipRepository.findByFanAndArtist(fan, artist)).thenReturn(Optional.empty());
		when(periodRepository.existsByFanIdAndArtistId(FAN, ARTIST)).thenReturn(false);

		assertTrue(service.isMembershipExpired(fan, artist));
		assertTrue(service.isNeverSubscribed(fan, artist));
	}

	// 가입했다가 기간이 지난 팬은 지금처럼 만료 배너
	@Test
	void expiredFanIsShownAsExpired() {
		when(membershipRepository.findByFanAndArtist(fan, artist))
				.thenReturn(Optional.of(membership(LocalDateTime.now().minusDays(1))));

		assertTrue(service.isMembershipExpired(fan, artist));
		assertFalse(service.isNeverSubscribed(fan, artist));
	}

	// 해지하면 membership 줄은 지워지지만 가입 이력이 남아 있으므로 처음 온 사람으로 보지 않는다
	@Test
	void cancelledFanWithHistoryIsShownAsExpired() {
		when(membershipRepository.findByFanAndArtist(fan, artist)).thenReturn(Optional.empty());
		when(periodRepository.existsByFanIdAndArtistId(FAN, ARTIST)).thenReturn(true);

		assertTrue(service.isMembershipExpired(fan, artist));
		assertFalse(service.isNeverSubscribed(fan, artist));
	}

	private static Membership membership(LocalDateTime expiresAt) {
		return Membership.builder().expiresAt(expiresAt).build();
	}

	private static User user(Long id) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		return user;
	}
}
