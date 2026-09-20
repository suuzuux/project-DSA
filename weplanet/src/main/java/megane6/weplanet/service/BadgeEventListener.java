package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.enumfolder.BadgeCode;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.repository.GroupFollowRepository;
import megane6.weplanet.repository.LikeRepository;
import megane6.weplanet.repository.PostRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BadgeEventListener {
	// 배지 목표치. 조건이 바뀌면 여기 숫자만 고치면 된다.
	private static final long COMMENT_GOAL = 5;
	private static final long LIKE_GIVEN_GOAL = 10;
	private static final long LIKE_RECEIVED_GOAL = 5;
	
	private final BadgeAwardService bas;
	private final PostRepository pr;
	private final CommentRepository cr;
	private final LikeRepository lr;
	private final GroupFollowRepository gfr;
	
	/**
	 * AFTER_COMMIT : 원래 작업(글 저장 등)이 DB에 "확정"된 뒤에 실행한다.
	 *   - 글 저장이 실패해서 롤백됐는데 배지만 나가는 일이 없다.
	 *   - 개수를 셀 때 방금 저장한 글/댓글까지 포함된다.
	 * fallbackExecution = true : PostService, CommentService 처럼 @Transactional 이 없는 곳에서
	 *   던진 이벤트는 "커밋"이라는 순간이 없어서 기본값이면 조용히 무시된다. 그래도 실행되게 한다.
	 */
	
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
										fallbackExecution = true)
	public void onActivity(BadgeActivityEvent event) {
		Long fanId = event.fanId();
		Long artistId = event.artistId();
		if (fanId == null || artistId == null || event.activity() == null) {
			return;
		}
		
		switch (event.activity()) {
			case COMMUNITY_JOINED -> {
				award(fanId, artistId, BadgeCode.BASIC_FIRST_JOIN);
				checkFirstPost(fanId, artistId);
				checkComments(fanId, artistId);
				checkLikesGiven(fanId, artistId);
				checkLikesReceived(fanId, artistId);
				checkFollow(fanId, artistId);
			}
			case POST_CREATED -> checkFirstPost(fanId, artistId);
			case COMMENT_CREATED -> checkComments(fanId, artistId);
			case LIKE_GIVEN -> checkLikesGiven(fanId, artistId);
			case LIKE_RECEIVED -> checkLikesReceived(fanId, artistId);
			case ARTIST_FOLLOWED -> checkFollow(fanId, artistId);
		}
	}
	
	// -------------------- 배지별 조건 확인 --------------------
	private void checkFirstPost(Long fanId, Long artistId) {
		if (pr.existsByAuthor_IdAndArtist_IdAndBoardType(fanId, artistId, BoardType.FAN)) {
			award(fanId, artistId, BadgeCode.BASIC_FIRST_POST);
		}
	}
	
	private void checkComments(Long fanId, Long artistId) {
		if (cr.countByAuthor_IdAndPost_Artist_Id(fanId, artistId) >= COMMENT_GOAL) {
			award(fanId, artistId, BadgeCode.BASIC_COMMENT_5);
		}
	}
	
	private void checkLikesGiven(Long fanId, Long artistId) {
		if (lr.countByUser_IdAndPost_Artist_Id(fanId, artistId) >= LIKE_GIVEN_GOAL) {
			award(fanId, artistId, BadgeCode.BASIC_LIKE_10);
		}
	}
	
	private void checkLikesReceived(Long fanId, Long artistId) {
		if (pr.sumLikeCountByAuthorAndArtist(fanId, artistId) >= LIKE_RECEIVED_GOAL) {
			award(fanId, artistId, BadgeCode.BASIC_LIKED_5);
		}
	}
	
	private void checkFollow(Long fanId, Long artistId) {
		if (gfr.existsByFanIdAndGroupId(fanId, artistId)) {
			award(fanId, artistId, BadgeCode.BASIC_FOLLOW_ARTIST);
		}
	}
	
	/**
	 * 지급 요청. 배지 지급이 실패해도 사용자의 원래 작업(글쓰기 등)은 이미 끝났으므로
	 * 에러를 화면까지 올리지 않고 로그만 남긴다.
	 * (동시에 같은 배지 요청이 두 번 들어와 UNIQUE 제약에 걸리는 경우도 여기서 조용히 처리됨)
	 */
	private void award(Long fanId, Long artistId, BadgeCode code) {
		try {
			bas.award(fanId, artistId, code);
		} catch (Exception e) {
			log.warn("배지 지급 실패 : fanId={}, artistId={}, badge={}, reason={}",
					fanId, artistId, code, e.getMessage());
		}
	}
}
