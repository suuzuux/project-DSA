package megane6.weplanet.service.email;

import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// GroupFollow/UserFollow 통합 이후: CommunityActivityNotifier의 발송 대상이
// "가입(CommunityMember) + 팔로우(UserFollow)" 둘 다 만족하는 사람으로 좁혀졌는지 검증.
// @Async는 스프링 프록시를 거칠 때만 동작하므로, 여기서는 new로 직접 생성해 동기 호출로 테스트한다.
class CommunityActivityNotifierTest {

	private final CommunityMemberRepository communityMemberRepository = mock(CommunityMemberRepository.class);
	private final UserFollowRepository userFollowRepository = mock(UserFollowRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final CommunityActivityEmailService emailService = mock(CommunityActivityEmailService.class);

	private final CommunityActivityNotifier notifier = new CommunityActivityNotifier(
			communityMemberRepository, userFollowRepository, userRepository, emailService);

	@Test
	void onlyMembersWhoAlsoFollowTheArtistReceiveTheEmail() {
		Long artistId = 14L;
		User artist = mock(User.class);
		when(artist.getId()).thenReturn(artistId);
		Post post = mock(Post.class);

		// fan101: 가입 + 팔로우 + 이메일알림 켬 -> 대상
		// fan102: 가입만, 팔로우 안 함 -> 제외
		// fan103: 가입 + 팔로우 O, 그러나 이메일알림 꺼짐 -> 제외
		when(communityMemberRepository.findByArtistId(artistId)).thenReturn(List.of(
				CommunityMember.builder().fanId(101L).artistId(artistId).build(),
				CommunityMember.builder().fanId(102L).artistId(artistId).build(),
				CommunityMember.builder().fanId(103L).artistId(artistId).build()
		));
		when(userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(101L, artistId, artistId))
				.thenReturn(true);
		when(userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(102L, artistId, artistId))
				.thenReturn(false);
		when(userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(103L, artistId, artistId))
				.thenReturn(true);

		User fan101 = mock(User.class);
		when(fan101.getId()).thenReturn(101L);
		when(fan101.isCommunityActivityEmailEnabled()).thenReturn(true);
		when(fan101.isNightNotificationAllowed()).thenReturn(true); // 실행 시각과 무관하게 통과시키기 위함

		User fan103 = mock(User.class);
		when(fan103.getId()).thenReturn(103L);
		when(fan103.isCommunityActivityEmailEnabled()).thenReturn(false);

		when(userRepository.findAllById(Set.of(101L, 103L))).thenReturn(List.of(fan101, fan103));

		notifier.notifyNewPost(artist, post);

		verify(emailService).sendNewPostEmail(fan101, artist, post);
		verify(emailService, never()).sendNewPostEmail(eq(fan103), eq(artist), eq(post));
	}

	@Test
	void noOneIsNotifiedWhenNoMemberFollowsTheArtist() {
		Long artistId = 20L;
		User artist = mock(User.class);
		when(artist.getId()).thenReturn(artistId);
		Post post = mock(Post.class);

		when(communityMemberRepository.findByArtistId(artistId)).thenReturn(List.of(
				CommunityMember.builder().fanId(201L).artistId(artistId).build()
		));
		when(userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(201L, artistId, artistId))
				.thenReturn(false);

		notifier.notifyNewPost(artist, post);

		verify(userRepository, never()).findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any());
		verify(emailService, never()).sendNewPostEmail(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void noOneIsNotifiedWhenCommunityHasNoMembers() {
		Long artistId = 30L;
		User artist = mock(User.class);
		when(artist.getId()).thenReturn(artistId);
		Post post = mock(Post.class);

		when(communityMemberRepository.findByArtistId(artistId)).thenReturn(List.of());

		notifier.notifyNewPost(artist, post);

		verify(userFollowRepository, never())
				.existsByFollowerIdAndFollowingIdAndCommunityId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		verify(emailService, never()).sendNewPostEmail(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}
}
