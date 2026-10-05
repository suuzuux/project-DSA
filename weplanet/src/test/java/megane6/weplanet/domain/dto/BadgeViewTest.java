package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.FanBadge;
import megane6.weplanet.i18n.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BadgeViewTest {

	// 배지 이름·설명은 배지 코드로 화면 언어 문구를 찾고, 문구가 없으면 DB 값(한국어)을 쓴다
	@Test
	void usesMessageByBadgeCodeOrFallsBackToDbValue() {
		FanBadge badge = mock(FanBadge.class);
		when(badge.getBadgeCode()).thenReturn("BASIC_FIRST_JOIN");
		when(badge.getBadgeName()).thenReturn("커뮤니티 첫 가입");
		when(badge.getDescription()).thenReturn("커뮤니티에 처음 가입하면 획득");
		Messages messages = mock(Messages.class);
		when(messages.getOrDefault("badge.BASIC_FIRST_JOIN.name", "커뮤니티 첫 가입")).thenReturn("First Community Join");
		when(messages.getOrDefault("badge.BASIC_FIRST_JOIN.description", "커뮤니티에 처음 가입하면 획득"))
				.thenReturn("커뮤니티에 처음 가입하면 획득");

		BadgeView view = BadgeView.of(badge, true, messages);

		assertEquals("First Community Join", view.badgeName());
		assertEquals("커뮤니티에 처음 가입하면 획득", view.description());
	}
}
