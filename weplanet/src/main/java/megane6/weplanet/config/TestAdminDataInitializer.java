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
 * 테스트용 최고관리자(ADMIN) 계정을 DB에 넣어둠.
 * <p>
 * 주의: 관리자 로그인은 아이디/비밀번호만으로 끝나지 않고, 로그인할 때마다
 * 이 계정의 email로 실제 인증번호 메일을 보낸다(AdminLoginService).
 * 그래서 email은 팀이 다 같이 확인할 수 있는 주소여야 의미가 있다 -
 * 기존에 로컬에 수동으로 만들어져 있던 admin_test 계정과 같은 주소를 그대로 씀.
 * 이 주소를 팀원 전체가 실제로 열어볼 수 없다면, 각자 자기 이메일로 바꿔서 써야 한다.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TestAdminDataInitializer implements ApplicationRunner {

	private static final String USERNAME = "admin_test";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.existsByUsername(USERNAME)) {
			return;
		}
		User admin = User.createAdmin(
				USERNAME,
				passwordEncoder.encode("Test1234"),
				"관리자테스트",
				"관리자테스트",
				"admin4.wp@gmail.com"
		);
		userRepository.save(admin);
		log.info("테스트 관리자 계정 생성: {}", USERNAME);
	}
}
