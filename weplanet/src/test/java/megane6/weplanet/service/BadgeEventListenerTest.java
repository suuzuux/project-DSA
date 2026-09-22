package megane6.weplanet.service;

import megane6.weplanet.domain.entity.MembershipPeriod;
import megane6.weplanet.domain.entity.enumfolder.BadgeCode;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.repository.LikeRepository;
import megane6.weplanet.repository.UserFollowRepository;
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
				mock(UserFollowRepository.class),
				periodRepository);

		when(periodRepository.findTopByFanIdAndArtistIdOrderByStartedAtDesc(11L, 22L))
				.thenReturn(Optional.of(MembershipPeriod.builder().streakCount(1).build()));

		listener.onActivity(new BadgeActivityEvent(
				11L, 22L, BadgeActivityEvent.Activity.MEMBERSHIP_JOINED));

		verify(awardService).award(11L, 22L, BadgeCode.SPECIAL_MEMBERSHIP_1);
	}

	// GroupFollow/UserFollow 통합 이후: 아티스트 팔로우(following_id == community_id == artistId)
	// 관계가 있을 때만 BASIC_FOLLOW_ARTIST 배지가 나가는지 확인.
	@Test
	void artistFollowedAwardsFollowBadgeWhenFollowRelationExists() {
		BadgeAwardService awardService = mock(BadgeAwardService.class);
		UserFollowRepository userFollowRepository = mock(UserFollowRepository.class);
		BadgeEventListener listener = new BadgeEventListener(
				awardService,
				mock(PostRepository.class),
				mock(CommentRepository.class),
				mock(LikeRepository.class),
				userFollowRepository,
				mock(MembershipPeriodRepository.class));

		when(userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(11L, 22L, 22L))
				.thenReturn(true);

		listener.onActivity(new BadgeActivityEvent(
				11L, 22L, BadgeActivityEvent.Activity.ARTIST_FOLLOWED));

		verify(awardService).award(11L, 22L, BadgeCode.BASIC_FOLLOW_ARTIST);
	}

	@Test
	void artistFollowedDoesNotAwardBadgeWhenFollowRelationMissing() {
		BadgeAwardService awardService = mock(BadgeAwardService.class);
		UserFollowRepository userFollowRepository = mock(UserFollowRepository.class);
		BadgeEventListener listener = new BadgeEventListener(
				awardService,
				mock(PostRepository.class),
				mock(CommentRepository.class),
				mock(LikeRepository.class),
				userFollowRepository,
				mock(MembershipPeriodRepository.class));

		when(userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(11L, 22L, 22L))
				.thenReturn(false);

		listener.onActivity(new BadgeActivityEvent(
				11L, 22L, BadgeActivityEvent.Activity.ARTIST_FOLLOWED));

		verify(awardService, org.mockito.Mockito.never()).award(11L, 22L, BadgeCode.BASIC_FOLLOW_ARTIST);
	}
}
