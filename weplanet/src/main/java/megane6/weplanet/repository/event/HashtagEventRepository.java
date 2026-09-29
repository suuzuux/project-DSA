package megane6.weplanet.repository.event;

import megane6.weplanet.domain.entity.event.HashtagEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HashtagEventRepository extends JpaRepository<HashtagEvent, Long> {
	
	// 관리자 목록: 최근(시작일이 늦은) 이벤트가 위로
	List<HashtagEvent> findAllByOrderByStartAtDesc();
	
	// 공개 페이지의 대표 이벤트 ①: 이미 시작한 이벤트 중 가장 최근 것 (진행 중이거나, 끝나서 결과를 보여줄 것)
	Optional<HashtagEvent> findFirstByStartAtLessThanEqualOrderByStartAtDesc(LocalDateTime now);
	
	// 대표 이벤트 ②: 시작한 이벤트가 하나도 없으면, 가장 가까운 예정 이벤트
	Optional<HashtagEvent> findFirstByStartAtAfterOrderByStartAtAsc(LocalDateTime now);
	
	
	// 기간이 겹치는 이벤트가 있는지 (동시 진행 1개 규칙)
	// 두 기간 [s1, e1], [s2, e2] 는 "s1 <= e2 이고 e1 >= s2" 일 때 겹친다
	// → 메서드 이름 순서대로 첫 번째 인자에 새 이벤트의 endAt, 두 번째 인자에 startAt 을 넣는다
	boolean existsByStartAtLessThanEqualAndEndAtGreaterThanEqual(LocalDateTime endAt, LocalDateTime startAt);
	
	// 수정할 때는 자기 자신과 겹치는 건 빼고 검사
	boolean existsByStartAtLessThanEqualAndEndAtGreaterThanEqualAndIdNot(
			LocalDateTime endAt, LocalDateTime startAt, Long id);
}