package megane6.weplanet.util;

import megane6.weplanet.repository.main.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NicknameGeneratorTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final NicknameGenerator generator = new NicknameGenerator(userRepository);

	@AfterEach
	void resetLocale() {
		LocaleContextHolder.resetLocaleContext();
	}

	// 화면 언어에 맞는 단어로 만들고, 어느 언어든 닉네임 규칙(2~15자)을 통과한다
	@Test
	void generatesNicknameInScreenLanguage() {
		assertAllGenerated(Locale.KOREAN, "^[가-힣]+\\d{3}$");
		assertAllGenerated(Locale.JAPANESE, "^[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]+\\d{3}$");
		assertAllGenerated(Locale.ENGLISH, "^[A-Za-z]+\\d{3}$");
	}

	// 계속 겹치면 화면 언어의 "게스트 + 시각" 닉네임으로 대신한다
	@Test
	void fallsBackToGuestNicknameInScreenLanguage() {
		when(userRepository.existsByNicknameAndRoleNotIn(anyString(), any())).thenReturn(true);

		assertGuest(Locale.KOREAN, "게스트");
		assertGuest(Locale.JAPANESE, "ゲスト");
		assertGuest(Locale.ENGLISH, "Guest");
	}

	private void assertAllGenerated(Locale locale, String pattern) {
		LocaleContextHolder.setLocale(locale);
		for (int i = 0; i < 200; i++) {
			String nickname = generator.generate();
			assertTrue(nickname.matches(pattern), locale + " " + nickname);
			assertTrue(NicknamePolicy.isAllowed(nickname), locale + " " + nickname);
		}
	}

	private void assertGuest(Locale locale, String prefix) {
		LocaleContextHolder.setLocale(locale);
		String nickname = generator.generate();
		assertTrue(nickname.startsWith(prefix), locale + " " + nickname);
		assertTrue(NicknamePolicy.isAllowed(nickname), locale + " " + nickname);
	}
}
