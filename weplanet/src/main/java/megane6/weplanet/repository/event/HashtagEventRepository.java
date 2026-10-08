package megane6.weplanet.repository.event;

import megane6.weplanet.domain.entity.event.HashtagEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HashtagEventRepository extends JpaRepository<HashtagEvent, Long> {
	
	// 관리자 목록: 최근(시작일이 늦은) 이벤트가 위로
	List<HashtagEvent> findAllByOrderByStartAtDesc();
	
	// 공개 페이지 대표 이벤트 ① 이미 시작한 이벤트 중 가장 최근 것
	Optional<HashtagEvent> findFirstByStartAtLessThanEqualOrderByStartAtDesc(LocalDateTime now);
	
	// 대표 이벤트 ② 시작한 이벤트가 없으면 가장 가까운 예정 이벤트
	Optional<HashtagEvent> findFirstByStartAtAfterOrderByStartAtAsc(LocalDateTime now);
	
	
	// 기간이 겹치는 이벤트가 있는지 (인자는 새 이벤트의 endAt, startAt 순).
	boolean existsByStartAtLessThanEqualAndEndAtGreaterThanEqual(LocalDateTime endAt, LocalDateTime startAt);
	
	// 수정 시 자기 자신은 빼고 검사
	boolean existsByStartAtLessThanEqualAndEndAtGreaterThanEqualAndIdNot(
			LocalDateTime endAt, LocalDateTime startAt, Long id);
}