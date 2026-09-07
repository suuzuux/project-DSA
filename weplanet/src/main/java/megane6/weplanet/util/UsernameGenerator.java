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
	
	// 소셜 로그인(구글 등) 최초 가입 시 username(로그인 아이디)을 자동 생성한다.
	// SignupRequestDto의 username 정규식(^[a-zA-Z0-9]{4,20}$)을 만족해야 하므로
	// 영문/숫자만 사용한다.
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