package megane6.weplanet.util;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class UsernameGenerator {
	
	private static final int MAX_ATTEMPTS = 10;
	
	private final UserRepository userRepository;
	
	// 소셜 가입 때 username(로그인 아이디)을 자동 생성한다 - 아이디 규칙(영문/숫자 4~20자)에 맞게 provider + 6자리 숫자.
	public String generate(AuthProvider provider) {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String candidate = randomCandidate(provider);
			if (!userRepository.existsByUsername(candidate)) {
				return candidate;
			}
		}
		String timestampSuffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
		return "user" + timestampSuffix;
	}
	
	private String randomCandidate(AuthProvider provider) {
		String prefix = provider.name().toLowerCase();
		int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000); // 6자리 숫자
		return prefix + suffix;
	}
}