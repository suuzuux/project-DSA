package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.FanBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FanBadgeRepository extends JpaRepository<FanBadge, Long> {
	/**
	 * 배지 카탈로그 전체 화면 표시 순서대로 조회
	 * 정렬이 "일반 -> 스페셜"인 이유 : badgeType 이 enum(EnumType.STRING)이라
	 * DB에는 'basic' / 'special' 문자열로 들어가 있고, 사전순으로 basic이 먼저
	 * 유형이 늘어나면 순서기준이 깨지므로 @Query로 명시적 순서 지정해야 함
	 */
	List<FanBadge> findAllByOrderByBadgeTypeAscSortOrderAsc();
	
	// 지급할 때 배지 이름/유형을 스냅샷으로 복사하려고 코드 하나로 카탈로그를 찾는다
	Optional<FanBadge> findByBadgeCode(String badgeCode);
}
