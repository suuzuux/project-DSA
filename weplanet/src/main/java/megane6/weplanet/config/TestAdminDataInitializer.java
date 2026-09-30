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
 * 주의: 현재 관리자 로그인은 아이디/비밀번호만으로 끝난다(관리자 로그인 화면 /admin/login 에서만 가능).
 * 이메일 인증번호 2단계 인증(AdminLoginService)은 만들어져 있지만 로그인 흐름에 연결하지 않기로 했다 (AUTH-11).
 * email은 기존에 로컬에 수동으로 만들어져 있던 admin_test 계정과 같은 주소를 그대로 씀.
 * 테스트 기간용 계정 - 최종 버전에서는 이 클래스를 빼고 관리자는 DB에서 직접 만든다.
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
