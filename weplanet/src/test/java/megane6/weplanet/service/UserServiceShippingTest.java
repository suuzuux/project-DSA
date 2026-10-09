package megane6.weplanet.service;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.util.NicknameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

// 굿즈 주문서에 입력한 배송지를 다음 주문서 기본값으로 남기는 규칙
class UserServiceShippingTest {

	private final UserService service = new UserService(mock(UserRepository.class), mock(PasswordEncoder.class),
			mock(NicknameGenerator.class), mock(ApplicationEventPublisher.class),
			mock(CommunityJoinService.class), mock(UserFollowRepository.class));

	// 처음 주문: 비어 있던 주소 3칸과 전화번호가 채워진다
	@Test
	void firstOrderFillsAddressAndPhone() {
		User user = fan();

		service.rememberShipping(user, "06236", "서울 강남구 테헤란로 1", "101동 202호", "010-1111-2222");

		assertEquals("06236", user.getZipcode());
		assertEquals("서울 강남구 테헤란로 1", user.getAddress1());
		assertEquals("101동 202호", user.getAddress2());
		assertEquals("010-1111-2222", user.getPhone());
	}

	// 다음 주문: 주소는 최근 배송지로 바뀌지만, 이미 있는 내 전화번호는 받는 사람 번호로 덮어쓰지 않는다 (선물 주문)
	@Test
	void laterOrderUpdatesAddressButKeepsMyPhone() {
		User user = fan();
		ReflectionTestUtils.setField(user, "phone", "010-1111-2222");

		service.rememberShipping(user, "48058", "부산 해운대구 1", null, "010-9999-8888");

		assertEquals("48058", user.getZipcode());
		assertEquals("부산 해운대구 1", user.getAddress1());
		assertNull(user.getAddress2());
		assertEquals("010-1111-2222", user.getPhone());
	}

	// 설정 화면 전화번호 규칙(숫자·+·-, 20자)에 맞지 않는 연락처는 내 전화번호로 넣지 않는다 - 넣으면 설정 저장이 막힘
	@Test
	void phoneNotMatchingSettingsRuleIsNotSaved() {
		User user = fan();

		service.rememberShipping(user, "06236", "서울 강남구 테헤란로 1", null, "010 1111 2222 (경비실)");

		assertEquals("06236", user.getZipcode());
		assertNull(user.getPhone());
	}

	private static User fan() {
		User user = User.createFan("fan07", "encoded", "홍길동", "닉네임", "fan07@test.com");
		ReflectionTestUtils.setField(user, "id", 7L);
		return user;
	}
}
