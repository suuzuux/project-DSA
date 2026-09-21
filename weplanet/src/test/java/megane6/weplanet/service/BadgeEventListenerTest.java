package megane6.weplanet.service;

import megane6.weplanet.domain.entity.MembershipPeriod;
import megane6.weplanet.domain.entity.enumfolder.BadgeCode;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.repository.GroupFollowRepository;
import megane6.weplanet.repository.LikeRepository;
import megane6.weplanet.repository.MembershipPeriodRepository;
import megane6.weplanet.repository.PostRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgeEventListenerTest {

	@Test
	void membershipJoinedAwardsFirstMembershipSpecialBadge() {
		BadgeAwardService awardService = mock(BadgeAwardService.class);
		MembershipPeriodRepository periodRepository = mock(MembershipPeriodRepository.class);
		BadgeEventListener listener = new BadgeEventListener(
				awardService,
				mock(PostRepository.class),
				mock(CommentRepository.class),
				mock(LikeRepository.class),
				mock(GroupFollowRepository.class),
				periodRepository);

		when(periodRepository.findTopByFanIdAndArtistIdOrderByStartedAtDesc(11L, 22L))
				.thenReturn(Optional.of(MembershipPeriod.builder().streakCount(1).build()));

		listener.onActivity(new BadgeActivityEvent(
				11L, 22L, BadgeActivityEvent.Activity.MEMBERSHIP_JOINED));

		verify(awardService).award(11L, 22L, BadgeCode.SPECIAL_MEMBERSHIP_1);
	}
}
