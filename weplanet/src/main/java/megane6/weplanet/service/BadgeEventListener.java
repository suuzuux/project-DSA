package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.MembershipPeriod;
import megane6.weplanet.domain.entity.enumfolder.BadgeCode;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BadgeEventListener {
	// 배지 목표치
	private static final long COMMENT_GOAL = 5;
	private static final long LIKE_GIVEN_GOAL = 10;
	private static final long LIKE_RECEIVED_GOAL = 5;
	
	private final BadgeAwardService bas;
	private final PostRepository pr;
	private final CommentRepository cr;
	private final LikeRepository lr;
	private final UserFollowRepository ufr;
	private final MembershipPeriodRepository mpr;
	
	/** 원래 작업이 커밋된 뒤 실행한다 (트랜잭션 없는 곳의 이벤트도 fallbackExecution 으로 실행). */
	
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
			// 한 번만 하면 되는 활동은 이벤트 자체가 조건 달성이다.
			case MEDIA_VIEWED -> award(fanId, artistId, BadgeCode.BASIC_MEDIA_VIEW);
			case LIVE_VIEWED -> award(fanId, artistId, BadgeCode.BASIC_LIVE_VIEW);
			case PROJECT_JOINED -> award(fanId, artistId, BadgeCode.SPECIAL_PROJECT_CREATE);
			case MEMBERSHIP_JOINED -> checkMembership(fanId, artistId);
		}
	}
	
	// 배지별 조건 확인
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
		// 아티스트 팔로우는 following_id == community_id == artistId
		if (ufr.existsByFollowerIdAndFollowingIdAndCommunityId(fanId, artistId, artistId)) {
			award(fanId, artistId, BadgeCode.BASIC_FOLLOW_ARTIST);
		}
	}
	
	/** 멤버십 연속 N년 배지 (가입 때 저장한 연속 횟수로 판단). */
	private void checkMembership(Long fanId, Long artistId) {
		int streak = mpr.findTopByFanIdAndArtistIdOrderByStartedAtDesc(fanId, artistId)
				.map(MembershipPeriod::getStreakCount)
				.orElse(0);
		
		BadgeCode code = switch (streak) {
			case 1 -> BadgeCode.SPECIAL_MEMBERSHIP_1;
			case 2 -> BadgeCode.SPECIAL_MEMBERSHIP_2;
			case 3 -> BadgeCode.SPECIAL_MEMBERSHIP_3;
			case 4 -> BadgeCode.SPECIAL_MEMBERSHIP_4;
			case 5 -> BadgeCode.SPECIAL_MEMBERSHIP_5;
			default -> null;		// 이력 없음 또는 6년차 이상은 배지 없음
		};
		if (code != null) {
			award(fanId, artistId, code);
		}
	}
	
	/** 지급 실패는 원래 작업에 영향을 주지 않고 로그만 남긴다 (동시 요청 UNIQUE 충돌 포함). */
	private void award(Long fanId, Long artistId, BadgeCode code) {
		try {
			bas.award(fanId, artistId, code);
		} catch (Exception e) {
			log.warn("배지 지급 실패 : fanId={}, artistId={}, badge={}, reason={}",
					fanId, artistId, code, e.getMessage());
		}
	}
}
