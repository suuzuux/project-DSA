package megane6.weplanet.service.community;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.portal.ArtistProfileRepository;
import megane6.weplanet.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 커뮤니티 탈퇴: 그 커뮤니티의 팬↔팬 팔로우(내가 한 것, 나를 한 것)는 지우고, 팬→아티스트 팔로우는 남기는지 확인한다
class CommunityLeaveFollowCleanupTest {

	private static final long FAN_ID = 1L;
	private static final long COMMUNITY_ID = 10L;

	private final CommunityMemberRepository memberRepository = mock(CommunityMemberRepository.class);
	private final UserFollowRepository followRepository = mock(UserFollowRepository.class);
	private final CommunityJoinService service = new CommunityJoinService(memberRepository,
			mock(ArtistProfileRepository.class), mock(UserRepository.class), mock(FileStorageService.class),
			mock(ApplicationEventPublisher.class), followRepository);

	private final User fan = User.createFan("fan01", "encoded", "이름", "닉네임", "fan01@test.com");

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(fan, "id", FAN_ID);
		when(memberRepository.findByFanIdAndArtistId(FAN_ID, COMMUNITY_ID)).thenReturn(Optional.of(
				CommunityMember.builder().fanId(FAN_ID).artistId(COMMUNITY_ID).nickname("닉네임").build()));
		TransactionSynchronizationManager.initSynchronization();   // 탈퇴 후 사진 정리를 커밋 뒤로 미루는 데 필요
	}

	@AfterEach
	void tearDown() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	@Test
	void leaveRemovesFanFollowsButKeepsArtistFollow() {
		service.leave(fan, COMMUNITY_ID);

		// 내가 이 커뮤니티에서 팔로우한 팬들 (아티스트 = 커뮤니티 주인은 제외 → 아티스트 팔로우는 남는다)
		verify(followRepository).deleteByCommunityIdAndFollowerIdAndFollowingIdNot(COMMUNITY_ID, FAN_ID, COMMUNITY_ID);
		// 이 커뮤니티에서 나를 팔로우한 팬들
		verify(followRepository).deleteByCommunityIdAndFollowingId(COMMUNITY_ID, FAN_ID);
	}
}
