package megane6.weplanet.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트용 에이전시 계정을 DB에 넣어둠.
 * portal/login.html에 안내된 "에이전시 테스트 계정: agency_wp / Test1234"와 짝을 맞춘다.
 * <p>
 * TestArtistDataInitializer가 아티스트 계정은 자동으로 만들어주는데,
 * 정작 agency_wp 로그인 계정(users, role=AGENCY) 자체를 만드는 코드가 없어서
 * 로컬 DB에 수동으로 만든 사람에게만 있고 다른 팀원 로컬엔 없던 문제를 해결한다.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TestAgencyDataInitializer implements ApplicationRunner {

	private static final String USERNAME = "agency_wp";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.existsByUsername(USERNAME)) {
			return;
		}
		User agency = User.createAgencyStaff(
				USERNAME,
				passwordEncoder.encode("Test1234"),
				"에이전시",
				"WePlaNet Agency",
				"agency@weplanet.test"
		);
		userRepository.save(agency);
		log.info("테스트 에이전시 계정 생성: {}", USERNAME);
	}
}
