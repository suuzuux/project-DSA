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
import java.time.LocalDate;

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
			// 와이어프레임 10번(급상승 커뮤니티 데뷔일) 표시를 위해 데뷔일도 임시로 채워둠
			// (실제 값은 없으니, 테스트가 매번 똑같이 보이도록 아티스트 id 기준으로 날짜를 살짝 다르게 잡음)
			LocalDate debutDate = LocalDate.of(2023, 1, 1).plusDays(artist.getId() * 37);
			ensureArtistGroup(artist, debutDate);
			ensureArtistGroupProfile(artist, debutDate);
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

	private void ensureArtistGroup(User artist, LocalDate debutDate) {
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
				INSERT INTO artist_groups (id, agency_id, name, name_en, fandom_name, debut_date, status, created_at, updated_at)
				VALUES (?, 1, ?, NULL, NULL, ?, 'ACTIVE', NOW(6), NOW(6))
				""", artist.getId(), artist.getNickname(), debutDate);
		log.info("아티스트 그룹 생성: id={} name={}", artist.getId(), artist.getNickname());
	}

	// EXPLORE-02 커뮤니티 검색(/community/search)이 artist_group_profiles 를 조인해서 조회하므로,
	// 이 테이블에 행이 없으면 아티스트가 users 테이블에 있어도 검색 결과에는 안 뜬다.
	// 실제 프로필 값은 없으니, 검색 기능 테스트가 가능하도록 임시로 채워둠.
	private void ensureArtistGroupProfile(User artist, LocalDate debutDate) {
		Integer exists = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM artist_group_profiles WHERE artist_id = ?",
				Integer.class,
				artist.getId()
		);
		if (exists != null && exists > 0) {
			return;
		}

		String gender = switch (artist.getUsername()) {
			case "artist_hwiwon" -> "FEMALE";
			case "artist_jungsik" -> "MALE";
			default -> "MIXED";
		};
		jdbcTemplate.update("""
				INSERT INTO artist_group_profiles (artist_id, gender, member_count, nationality, category, debut_date, updated_at)
				VALUES (?, ?, 1, 'KR', '아이돌', ?, NOW(6))
				""", artist.getId(), gender, debutDate);
		log.info("아티스트 프로필(artist_group_profiles) 생성: id={} name={}", artist.getId(), artist.getNickname());
	}
}
