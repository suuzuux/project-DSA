package megane6.weplanet.service.community;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.community.CommunityProfile;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.community.CommunityProfileRepository;
import megane6.weplanet.repository.portal.ArtistProfileRepository;
import megane6.weplanet.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 커뮤니티 프로필 편집: 디스크의 사진 파일은 DB 와 달리 롤백되지 않으므로, 저장 결과에 맞춰 정리하는지 확인한다
class CommunityJoinServiceFileCleanupTest {

	private final CommunityMemberRepository memberRepository = mock(CommunityMemberRepository.class);
	private final CommunityProfileRepository profileRepository = mock(CommunityProfileRepository.class);
	private final FileStorageService fileStorageService = mock(FileStorageService.class);
	private final CommunityJoinService service = new CommunityJoinService(memberRepository, profileRepository,
			mock(ArtistProfileRepository.class), mock(UserRepository.class), fileStorageService,
			mock(ApplicationEventPublisher.class), mock(UserFollowRepository.class));

	private final User fan = User.createFan("fan01", "encoded", "이름", "닉네임", "fan01@test.com");
	private final MockMultipartFile newAvatar = new MockMultipartFile("avatar", "a.jpg", "image/jpeg", new byte[]{1});
	private final MockMultipartFile badBackground = new MockMultipartFile("background", "b.heic", "image/heic", new byte[]{2});
	private CommunityProfile profile;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(fan, "id", 1L);
		CommunityMember member = CommunityMember.builder().fanId(1L).artistId(10L).build();
		profile = CommunityProfile.builder().communityMember(member).nickname("닉네임")
				.avatarStoredName("old-avatar.jpg").backgroundStoredName("old-bg.jpg").build();
		when(memberRepository.findByFanIdAndArtistId(1L, 10L)).thenReturn(Optional.of(member));
		when(profileRepository.findByCommunityMember_Id(any())).thenReturn(Optional.of(profile));
		when(fileStorageService.storeImage(newAvatar)).thenReturn("new-avatar.jpg");
		when(fileStorageService.storeImage(badBackground)).thenThrow(new IllegalArgumentException("error.upload.imageTypeInvalid"));
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void tearDown() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	// 새 프로필 사진은 저장됐는데 배경 사진이 실패해서 롤백되면: 옛 사진 파일은 남기고(DB 가 계속 가리킴), 새 파일만 지운다.
	// 예전에는 옛 사진 파일을 먼저 지워서 롤백 뒤 프로필 사진이 깨졌다.
	@Test
	void rollbackKeepsOldAvatarAndRemovesNewFile() {
		assertThrows(IllegalArgumentException.class, () -> service.editProfile(fan, 10L, null, null,
				newAvatar, badBackground, false, false, false));

		complete(TransactionSynchronization.STATUS_ROLLED_BACK);

		verify(fileStorageService).delete("new-avatar.jpg");
		verify(fileStorageService, never()).delete("old-avatar.jpg");
		verify(fileStorageService, never()).delete("old-bg.jpg");
	}

	// 정상 저장이면 옛 사진은 저장이 확정된 뒤에만 지운다
	@Test
	void oldAvatarIsDeletedOnlyAfterCommit() {
		service.editProfile(fan, 10L, null, null, newAvatar, null, false, false, false);
		verify(fileStorageService, never()).delete(any());

		complete(TransactionSynchronization.STATUS_COMMITTED);

		verify(fileStorageService).delete("old-avatar.jpg");
		verify(fileStorageService, never()).delete("new-avatar.jpg");
		assertEquals("new-avatar.jpg", profile.getAvatarStoredName());
	}

	// 커뮤니티 탈퇴도 사진 파일은 탈퇴가 확정된 뒤에 지운다
	@Test
	void leaveDeletesProfileFilesAfterCommit() {
		service.leave(fan, 10L);
		verify(fileStorageService, never()).delete(any());

		complete(TransactionSynchronization.STATUS_COMMITTED);

		verify(fileStorageService).delete("old-avatar.jpg");
		verify(fileStorageService).delete("old-bg.jpg");
	}

	private static void complete(int status) {
		TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
	}
}
