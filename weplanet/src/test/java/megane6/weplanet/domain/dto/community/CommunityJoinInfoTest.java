package megane6.weplanet.domain.dto.community;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommunityJoinInfoTest {

	@Test
	void 가입_당일은_D플러스1이다() {
		CommunityJoinInfo info = CommunityJoinInfo.from(
				LocalDateTime.of(2026, 9, 1, 23, 59),
				LocalDate.of(2026, 9, 1)
		);

		assertEquals(1, info.dayNumber());
		assertEquals(LocalDate.of(2026, 9, 1), info.joinedDate());
	}

	@Test
	void 가입_이틀_뒤는_D플러스3이다() {
		CommunityJoinInfo info = CommunityJoinInfo.from(
				LocalDateTime.of(2026, 8, 30, 23, 59),
				LocalDate.of(2026, 9, 1)
		);

		assertEquals(3, info.dayNumber());
	}
}
