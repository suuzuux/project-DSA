package megane6.weplanet.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 테스트용 아티스트 계정을 DB에 넣어둠.
 * 이전 시드(RIIZE/aespa/CORTIS)는 제거하고, 휘원공주·정식왕자만 유지.
 * 로그인 비밀번호: Test1234
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestArtistDataInitializer implements ApplicationRunner {

	private static final List<String> LEGACY_ARTIST_USERNAMES = List.of(
			"artist_riize",
			"artist_aespa",
			"artist_cortis"
	);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		removeLegacyArtists();
		seed("artist_hwiwon", "휘원공주", "휘원", "hwiwon@weplanet.test");
		seed("artist_jungsik", "정식왕자", "정식", "jungsik@weplanet.test");
	}

	private void removeLegacyArtists() {
		for (String username : LEGACY_ARTIST_USERNAMES) {
			userRepository.findByUsername(username).ifPresent(user -> {
				userRepository.delete(user);
				log.info("이전 테스트 아티스트 삭제: {}", username);
			});
		}
	}

	private void seed(String username, String nickname, String realName, String email) {
		if (userRepository.existsByUsername(username)) {
			return;
		}
		User artist = User.createArtist(
				username,
				passwordEncoder.encode("Test1234"),
				realName,
				nickname,
				email
		);
		userRepository.save(artist);
		log.info("테스트 아티스트 생성: {} ({})", username, nickname);
	}
}
