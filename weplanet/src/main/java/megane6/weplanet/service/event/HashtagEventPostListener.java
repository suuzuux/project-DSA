package megane6.weplanet.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.event.FanPostCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 팬 게시글 저장 후 총공 집계 리스너 (트랜잭션 프록시 때문에 서비스를 분리하고, 실패는 로그만 남김). */
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
