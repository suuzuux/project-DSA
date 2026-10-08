package megane6.weplanet.service;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.util.NicknameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 회원 탈퇴: 비밀번호를 확인한 뒤 개인정보를 알아볼 수 없게 바꾸고(익명화), 가입한 커뮤니티와 팔로우를 정리한다
class UserServiceWithdrawTest {

	private static final long USER_ID = 7L;

	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final CommunityJoinService communityJoinService = mock(CommunityJoinService.class);
	private final UserFollowRepository followRepository = mock(UserFollowRepository.class);
	private final UserService service = new UserService(mock(UserRepository.class), passwordEncoder,
			mock(NicknameGenerator.class), mock(ApplicationEventPublisher.class), communityJoinService, followRepository);

	@Test
	void withdrawAnonymizesPersonalInfo() {
		User user = fanWithPersonalInfo();
		when(passwordEncoder.matches("abcd123!", "encoded")).thenReturn(true);
		when(communityJoinService.joinedArtistIds(user)).thenReturn(Set.of(1101L));

		service.withdraw(user, "abcd123!");

		assertEquals(UserStatus.WITHDRAWN, user.getStatus());
		assertNotNull(user.getDeletedAt());
		// 아이디·이메일은 다시 쓸 수 있도록 withdrawn_{번호}로, 실명은 고정 문구로
		assertEquals("withdrawn_7", user.getUsername());
		assertEquals("withdrawn_7@withdrawn.weplanet.local", user.getEmail());
		assertEquals("탈퇴한 회원", user.getRealName());
		// 선택 정보는 지운다 (개인정보처리방침: 탈퇴 시 익명 처리, 선택 항목 삭제)
		assertNull(user.getPhone());
		assertNull(user.getAddress1());
		assertNull(user.getBirthDate());
		// 가입한 커뮤니티 탈퇴 + 남은 팔로우 관계 정리
		verify(communityJoinService).leave(user, 1101L);
		verify(followRepository).deleteByFollowerId(USER_ID);
		verify(followRepository).deleteByFollowingId(USER_ID);
	}

	// 비밀번호가 틀리면 탈퇴하지 않고 계정·개인정보를 그대로 둔다
	@Test
	void wrongPasswordKeepsAccount() {
		User user = fanWithPersonalInfo();
		when(passwordEncoder.matches("wrong-pass1!", "encoded")).thenReturn(false);

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.withdraw(user, "wrong-pass1!"));

		assertEquals("settings.withdraw.passwordMismatch", e.getMessage());
		assertEquals(UserStatus.ACTIVE, user.getStatus());
		assertEquals("fan07", user.getUsername());
		assertEquals("010-1234-5678", user.getPhone());
		verifyNoInteractions(communityJoinService, followRepository);
	}

	// 소셜로만 가입해 비밀번호가 없는 계정은 확인 없이 탈퇴하고, 소셜 고유 ID 도 익명화한다
	// (그래야 같은 구글 계정으로 다시 가입할 수 있다)
	@Test
	void socialOnlyAccountWithdrawsWithoutPassword() {
		User social = User.createSocialFan("google123456", null, "권형준", "닉네임", "kwon@gmail.com",
				AuthProvider.GOOGLE, "google-sub-1");
		ReflectionTestUtils.setField(social, "id", 8L);

		service.withdraw(social, null);

		assertEquals(UserStatus.WITHDRAWN, social.getStatus());
		assertEquals("withdrawn_8", social.getProviderId());
		assertEquals("withdrawn_8@withdrawn.weplanet.local", social.getEmail());
	}

	private static User fanWithPersonalInfo() {
		User user = User.createFan("fan07", "encoded", "권형준", "닉네임", "fan07@test.com");
		ReflectionTestUtils.setField(user, "id", USER_ID);
		ReflectionTestUtils.setField(user, "phone", "010-1234-5678");
		ReflectionTestUtils.setField(user, "address1", "서울시 어딘가 1");
		ReflectionTestUtils.setField(user, "birthDate", LocalDate.of(2000, 1, 5));
		return user;
	}
}
