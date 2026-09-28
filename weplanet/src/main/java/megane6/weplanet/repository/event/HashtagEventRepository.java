package megane6.weplanet.repository.event;

import megane6.weplanet.domain.entity.event.HashtagEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface HashtagEventRepository extends JpaRepository<HashtagEvent, Long> {
	
	// 관리자 목록: 최근(시작일이 늦은) 이벤트가 위로
	List<HashtagEvent> findAllByOrderByStartAtDesc();
	
	// 기간이 겹치는 이벤트가 있는지 (동시 진행 1개 규칙)
	// 두 기간 [s1, e1], [s2, e2] 는 "s1 <= e2 이고 e1 >= s2" 일 때 겹친다
	// → 메서드 이름 순서대로 첫 번째 인자에 새 이벤트의 endAt, 두 번째 인자에 startAt 을 넣는다
	boolean existsByStartAtLessThanEqualAndEndAtGreaterThanEqual(LocalDateTime endAt, LocalDateTime startAt);
	
	// 수정할 때는 자기 자신과 겹치는 건 빼고 검사
	boolean existsByStartAtLessThanEqualAndEndAtGreaterThanEqualAndIdNot(
			LocalDateTime endAt, LocalDateTime startAt, Long id);
}