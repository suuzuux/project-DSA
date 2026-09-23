package megane6.weplanet.service.community;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.community.CommunityProfile;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.community.CommunityProfileRepository;
import megane6.weplanet.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityJoinServiceTest {

	@Test
	void authorViewsUseCommunityAvatarAndRespectHiddenProfile() {
		CommunityProfileRepository profileRepository = mock(CommunityProfileRepository.class);
		CommunityJoinService service = new CommunityJoinService(
				mock(CommunityMemberRepository.class),
				profileRepository,
				mock(UserRepository.class),
				mock(FileStorageService.class),
				mock(ApplicationEventPublisher.class),
				mock(UserFollowRepository.class));
		User visibleAuthor = author(11L, "계정닉네임");
		User hiddenAuthor = author(12L, "숨김계정");
		CommunityProfile visibleProfile = profile(11L, "커뮤니티닉", "avatar.png", false);
		CommunityProfile hiddenProfile = profile(12L, "비공개닉", "hidden.png", true);
		when(profileRepository.findForAuthorsInCommunity(eq(22L), anyCollection()))
				.thenReturn(List.of(visibleProfile, hiddenProfile));

		var views = service.authorViewsByAuthorIdKey(List.of(visibleAuthor, hiddenAuthor), 22L);

		assertEquals("커뮤니티닉", views.get("11").nickname());
		assertEquals("/uploads/avatar.png", views.get("11").avatarUrl());
		assertEquals("비공개닉", views.get("12").nickname());
		assertNull(views.get("12").avatarUrl());
		verify(profileRepository).findForAuthorsInCommunity(eq(22L), anyCollection());
	}

	private User author(Long id, String nickname) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getNickname()).thenReturn(nickname);
		return user;
	}

	private CommunityProfile profile(Long authorId, String nickname, String avatar, boolean hidden) {
		return CommunityProfile.builder()
				.communityMember(CommunityMember.builder().fanId(authorId).artistId(22L).build())
				.nickname(nickname)
				.avatarStoredName(avatar)
				.contentHidden(hidden)
				.build();
	}
}
