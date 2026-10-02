package megane6.weplanet.service;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.service.community.CommunityJoinService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserFollowServiceTest {

	private static final Long ME = 1L;
	private static final Long TARGET = 2L;
	private static final Long COMMUNITY = 10L;

	private final UserFollowRepository followRepository = mock(UserFollowRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final CommunityMemberRepository memberRepository = mock(CommunityMemberRepository.class);
	private final UserFollowService service = new UserFollowService(followRepository, userRepository, memberRepository,
			mock(CommunityJoinService.class), mock(ApplicationEventPublisher.class));

	// 상대가 커뮤니티를 떠났고 나도 가입돼 있지 않아도, 이미 걸려 있는 팔로우는 취소할 수 있어야 한다
	@Test
	void unfollowIsAllowedEvenWhenFollowConditionsNoLongerHold() {
		User me = user(ME, Role.FAN);
		when(followRepository.existsByFollowerIdAndFollowingIdAndCommunityId(ME, TARGET, COMMUNITY)).thenReturn(true);
		when(memberRepository.existsByFanIdAndArtistId(any(), any())).thenReturn(false);

		assertFalse(service.toggle(me, TARGET, COMMUNITY));

		verify(followRepository).deleteByFollowerIdAndFollowingIdAndCommunityId(ME, TARGET, COMMUNITY);
	}

	// 새로 팔로우할 때는 지금처럼 가입 조건을 검사한다
	@Test
	void newFollowStillRequiresBothToBeJoined() {
		User me = user(ME, Role.FAN);
		User target = user(TARGET, Role.FAN);
		when(followRepository.existsByFollowerIdAndFollowingIdAndCommunityId(ME, TARGET, COMMUNITY)).thenReturn(false);
		when(userRepository.findById(TARGET)).thenReturn(Optional.of(target));
		when(memberRepository.existsByFanIdAndArtistId(ME, COMMUNITY)).thenReturn(false);

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.toggle(me, TARGET, COMMUNITY));

		assertEquals("error.follow.joinRequired", e.getMessage());
		verify(followRepository, never()).save(any());
	}

	private static User user(Long id, Role role) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getRole()).thenReturn(role);
		when(user.canParticipateInCommunity()).thenReturn(true);
		return user;
	}
}
