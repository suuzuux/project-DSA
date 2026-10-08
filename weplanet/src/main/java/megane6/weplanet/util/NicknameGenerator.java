package megane6.weplanet.util;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class NicknameGenerator {
	
	private static final int MAX_ATTEMPTS = 10;
	
	// 언어별 단어 ("형용사 + 동물 + 3자리 숫자", 2~15자)
	private static final Words KO = new Words("게스트",
			List.of("행복한", "즐거운", "용감한", "차분한", "빛나는", "따뜻한", "씩씩한", "포근한"),
			List.of("고양이", "강아지", "토끼", "여우", "판다", "펭귄", "다람쥐", "부엉이"));
	private static final Words JA = new Words("ゲスト",
			List.of("さわやか", "げんきな", "たのしい", "やさしい", "きらきら", "ぽかぽか", "ふわふわ", "のんびり"),
			List.of("猫", "犬", "うさぎ", "きつね", "パンダ", "ペンギン", "リス", "ふくろう"));
	private static final Words EN = new Words("Guest",
			List.of("Sunny", "Happy", "Merry", "Brave", "Calm", "Shiny", "Warm", "Cozy"),
			List.of("Cat", "Puppy", "Bunny", "Fox", "Panda", "Owl", "Otter", "Koala"));
	
	private final UserRepository userRepository;
	
	// 현재 화면 언어의 단어로 만든다.
	public String generate() {
		Words words = wordsFor(LocaleContextHolder.getLocale());
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String candidate = randomCandidate(words);
			// 팬 쪽 계정끼리만 겹치지 않으면 된다.
			if (!userRepository.existsByNicknameAndRoleNotIn(candidate, Role.ARTIST_SIDE)) {
				return candidate;
			}
		}
		String timestampSuffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
		return words.guest() + timestampSuffix;
	}
	
	private static Words wordsFor(Locale locale) {
		String language = locale == null ? "" : locale.getLanguage();
		return switch (language) {
			case "ja" -> JA;
			case "en" -> EN;
			default -> KO;
		};
	}
	
	private String randomCandidate(Words words) {
		String adjective = pickRandom(words.adjectives());
		String noun = pickRandom(words.nouns());
		int suffix = ThreadLocalRandom.current().nextInt(100, 1000); // 3자리 숫자
		return adjective + noun + suffix;
	}
	
	private String pickRandom(List<String> list) {
		int index = ThreadLocalRandom.current().nextInt(list.size());
		return list.get(index);
	}
	
	private record Words(String guest, List<String> adjectives, List<String> nouns) {}
}