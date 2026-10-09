package megane6.weplanet.domain.entity;

import jakarta.persistence.EntityManager;
import megane6.weplanet.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// 실명·전화번호 암호화를 실제 DB 로 확인한다. @Transactional 이라 테스트가 끝나면 넣은 행은 모두 되돌린다
@SpringBootTest
@Transactional
class UserPersonalDataEncryptionTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private EntityManager entityManager;

	// 새로 가입한 회원: DB 에는 원래 글자가 보이지 않고(표시 바이트 0x01 로 시작), 엔티티로 읽으면 원래 값이 나온다
	@Test
	void newMemberRealNameAndPhoneAreStoredEncrypted() {
		User user = User.createFan("enc_test_member", null, "암호화테스트", "암호화닉", "enc_test_member@test.local");
		user.changePhone("010-1234-5678");
		userRepository.saveAndFlush(user);

		byte[] rawName = raw("real_name", user.getId());
		byte[] rawPhone = raw("phone", user.getId());
		assertEquals(0x01, rawName[0]);
		assertEquals(0x01, rawPhone[0]);
		assertFalse(new String(rawName, StandardCharsets.UTF_8).contains("암호화테스트"));
		assertFalse(new String(rawPhone, StandardCharsets.UTF_8).contains("1234"));

		entityManager.clear();
		User loaded = userRepository.findById(user.getId()).orElseThrow();
		assertEquals("암호화테스트", loaded.getRealName());
		assertEquals("010-1234-5678", loaded.getPhone());
	}

	// 기존 회원(평문 실명): 로그인처럼 다른 칸이 바뀌어도 실명은 평문 그대로, 새로 입력한 전화번호만 암호화된다
	@Test
	void existingPlainRealNameStaysPlainWhenOtherColumnsChange() {
		byte[] plainName = "기존회원".getBytes(StandardCharsets.UTF_8);
		jdbcTemplate.update("""
				INSERT INTO users (username, role, status, real_name, nickname, email, created_at, updated_at)
				VALUES ('enc_test_legacy', 'FAN', 'ACTIVE', ?, '기존닉', 'enc_test_legacy@test.local', NOW(6), NOW(6))
				""", (Object) plainName);
		Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'enc_test_legacy'", Long.class);

		User user = userRepository.findById(id).orElseThrow();
		assertEquals("기존회원", user.getRealName());
		user.recordLogin();
		userRepository.flush();
		assertArrayEquals(plainName, raw("real_name", id));

		user.changePhone("010-5555-6666");
		userRepository.flush();
		assertArrayEquals(plainName, raw("real_name", id));
		assertEquals(0x01, raw("phone", id)[0]);
	}

	private byte[] raw(String column, Long id) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM users WHERE id = ?", byte[].class, id);
	}
}
