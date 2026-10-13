package megane6.weplanet.repository.fan;

import megane6.weplanet.domain.entity.FanBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FanBadgeRepository extends JpaRepository<FanBadge, Long> {
	/** 배지 카탈로그를 일반 → 스페셜, 표시 순서로 조회 */
	List<FanBadge> findAllByOrderByBadgeTypeAscSortOrderAsc();
	
	// 지급 시 이름·유형을 복사하려고 코드로 카탈로그를 찾는다.
	Optional<FanBadge> findByBadgeCode(String badgeCode);
}
