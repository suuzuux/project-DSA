package megane6.weplanet.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FanProjectCommunityAccessRepository {
	
	private final JdbcTemplate jdbcTemplate;
	
	public boolean existsByFanIdAndArtistId(Long fanId, Long artistId) {
		Long count = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*)
				FROM community_members
				WHERE fan_id = ?
				  AND artist_id = ?
				""",
				Long.class,
				fanId,
				artistId
		);
		
		return count != null && count > 0;
	}
}