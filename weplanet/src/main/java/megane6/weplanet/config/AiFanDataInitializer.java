package megane6.weplanet.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.AiFanPersona;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아티스트 DM에 답장할 가상 AI 팬 계정 5명을 없으면 만들어 둔다.
 * 이미 DB를 쓰고 있는 팀원도 앱만 재시작하면 계정이 생기도록 시드 SQL과 별도로 둔다.
 * 로그인 비밀번호: Test1234
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class AiFanDataInitializer implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		String encoded = passwordEncoder.encode("Test1234");
		for (AiFanPersona persona : AiFanPersona.ALL) {
			if (userRepository.existsByUsername(persona.username())) {
				continue;
			}
			User fan = User.createFan(
					persona.username(),
					encoded,
					persona.nickname(),
					persona.nickname(),
					persona.username() + "@weplanet.test"
			);
			userRepository.save(fan);
			log.info("AI 팬 계정 생성: {} ({})", persona.username(), persona.nickname());
		}
	}
}
