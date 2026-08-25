package megane6.weplanet.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * post.artist_id 컬럼이 없으면 추가한다. (커뮤니티별 게시글 분리)
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class PostArtistColumnInitializer implements ApplicationRunner {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void run(ApplicationArguments args) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE()
				  AND TABLE_NAME = 'post'
				  AND COLUMN_NAME = 'artist_id'
				""", Integer.class);

		if (count != null && count > 0) {
			return;
		}

		jdbcTemplate.execute("""
				ALTER TABLE post
				ADD COLUMN artist_id BIGINT NULL AFTER board_type,
				ADD KEY idx_post_artist_board (artist_id, board_type, created_at),
				ADD CONSTRAINT fk_post_artist FOREIGN KEY (artist_id) REFERENCES users (id)
				""");
		log.info("post.artist_id 컬럼 추가 완료");
	}
}
