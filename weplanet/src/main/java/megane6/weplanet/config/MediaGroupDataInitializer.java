package megane6.weplanet.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 커뮤니티 Media 탭이 board_media.group_id(=아티스트 users.id) 를 쓰도록
 * agencies / artist_groups 를 시드한다. (FK 충족)
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class MediaGroupDataInitializer implements ApplicationRunner {

	private final JdbcTemplate jdbcTemplate;
	private final UserRepository userRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		ensureAgency();
		List<User> artists = userRepository.findByRole(Role.ARTIST);
		for (User artist : artists) {
			ensureArtistGroup(artist);
		}
	}

	private void ensureAgency() {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agencies", Integer.class);
		if (count != null && count > 0) {
			return;
		}
		jdbcTemplate.update("""
				INSERT INTO agencies (id, name, business_no, ceo_name, status, created_at, updated_at)
				VALUES (1, 'WePlaNet Agency', '000-00-00000', '테스트', 'ACTIVE', NOW(6), NOW(6))
				""");
		log.info("테스트 소속사(agencies id=1) 생성");
	}

	private void ensureArtistGroup(User artist) {
		Integer exists = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM artist_groups WHERE id = ?",
				Integer.class,
				artist.getId()
		);
		if (exists != null && exists > 0) {
			return;
		}

		// group_id 를 커뮤니티 artistId 와 동일하게 맞춰 Media 조회/업로드를 단순화
		jdbcTemplate.update("""
				INSERT INTO artist_groups (id, agency_id, name, name_en, fandom_name, status, created_at, updated_at)
				VALUES (?, 1, ?, NULL, NULL, 'ACTIVE', NOW(6), NOW(6))
				""", artist.getId(), artist.getNickname());
		log.info("아티스트 그룹 생성: id={} name={}", artist.getId(), artist.getNickname());
	}
}
