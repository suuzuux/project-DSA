package megane6.weplanet.util;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class NicknameGenerator {
	
	private static final int MAX_ATTEMPTS = 10;
	
	private static final List<String> ADJECTIVES = List.of(
			"행복한", "즐거운", "용감한", "차분한", "빛나는", "따뜻한", "씩씩한", "포근한"
	);
	private static final List<String> NOUNS = List.of(
			"고양이", "강아지", "토끼", "여우", "판다", "펭귄", "다람쥐", "부엉이"
	);
	
	private final UserRepository userRepository;
	
	public String generate() {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String candidate = randomCandidate();
			if (!userRepository.existsByNickname(candidate)) {
				return candidate;
			}
		}
		String timestampSuffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
		return "게스트" + timestampSuffix;
	}
	
	private String randomCandidate() {
		String adjective = pickRandom(ADJECTIVES);
		String noun = pickRandom(NOUNS);
		int suffix = ThreadLocalRandom.current().nextInt(100, 1000); // 3자리 숫자
		return adjective + noun + suffix;
	}
	
	private String pickRandom(List<String> list) {
		int index = ThreadLocalRandom.current().nextInt(list.size());
		return list.get(index);
	}
}