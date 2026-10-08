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

/** 커뮤니티 Media 탭용 agencies / artist_groups 기본 데이터를 시드한다. */
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
			// 시연 화면이 매번 같도록 아티스트 id 기준으로 임시 데뷔일을 채운다.
			LocalDate debutDate = LocalDate.of(2023, 1, 1).plusDays(artist.getId() * 37);
			ensureArtistGroup(artist, debutDate);
			fillExploreFiltersIfEmpty(artist, debutDate);
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

		// group_id 를 커뮤니티 artistId 와 같게 맞춰 Media 조회·업로드를 단순화한다.
		jdbcTemplate.update("""
				INSERT INTO artist_groups (id, agency_id, name, name_en, fandom_name, debut_date, status, created_at, updated_at)
				VALUES (?, 1, ?, NULL, NULL, ?, 'ACTIVE', NOW(6), NOW(6))
				""", artist.getId(), artist.getNickname(), debutDate);
		log.info("아티스트 그룹 생성: id={} name={}", artist.getId(), artist.getNickname());
	}

	// 커뮤니티 검색용 탐색 필터가 비어 있을 때만 임시 값으로 채운다 (포털 입력값은 덮어쓰지 않음).
	private void fillExploreFiltersIfEmpty(User artist, LocalDate debutDate) {
		String gender = switch (artist.getUsername()) {
			case "artist_hwiwon" -> "FEMALE";
			case "artist_jungsik" -> "MALE";
			default -> "MIXED";
		};
		int updated = jdbcTemplate.update("""
				UPDATE artist_groups
				SET gender = COALESCE(gender, ?),
				    member_count = 1,
				    nationality = COALESCE(nationality, 'KR'),
				    category = COALESCE(category, '아이돌'),
				    debut_date = COALESCE(debut_date, ?),
				    updated_at = NOW(6)
				WHERE id = ? AND member_count IS NULL
				""", gender, debutDate, artist.getId());
		if (updated > 0) {
			log.info("커뮤니티 탐색 필터 채움: id={} name={}", artist.getId(), artist.getNickname());
		}
	}
}
