package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ArtistGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistGroupRepository extends JpaRepository<ArtistGroup, Long> {
	
	// artist_groups.name 은 UNIQUE(uk_group_name) - 저장 전에 미리 확인해서 친절한 메시지를 준다
	boolean existsByName(String name);
}
