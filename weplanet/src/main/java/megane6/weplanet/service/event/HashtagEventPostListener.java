package megane6.weplanet.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.event.FanPostCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 팬 게시글이 저장되면 해시태그 총공 집계를 시도한다. (BadgeEventListener 와 같은 방식)
 *
 * AFTER_COMMIT + fallbackExecution : 글 저장이 확정된 뒤에 실행, PostService 처럼 @Transactional 이
 *   없는 곳에서 던진 이벤트도 실행되게 한다. (자세한 설명은 BadgeEventListener 주석 참고)
 *
 * 왜 리스너와 HashtagEventEntryService 를 나눴나?
 *   - @Transactional 은 "다른 빈이 호출할 때"만 동작한다 (스프링이 끼워 넣은 프록시를 거쳐야 해서).
 *     같은 클래스 안에서 자기 메서드를 부르면 트랜잭션이 안 걸린다.
 *   - 그리고 집계가 실패해도 글쓰기는 성공해야 하므로, 트랜잭션 "바깥"인 여기서 예외를 잡아 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HashtagEventPostListener {
	
	private final HashtagEventEntryService hees;
	
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,fallbackExecution = true)
	public void onFanPostCreated(FanPostCreatedEvent event) {
		try {
			hees.recordFanPost(event.postId());
		} catch (RuntimeException e) {
			log.warn("해시태그 총공 집계 기록 실패: postId={}", event.postId(), e);
		}
	}
}
