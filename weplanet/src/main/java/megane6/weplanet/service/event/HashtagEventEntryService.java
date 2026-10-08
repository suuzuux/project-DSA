package megane6.weplanet.service.event;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.enumfolder.events.HashtagEntryStatus;
import megane6.weplanet.domain.entity.event.HashtagEventEntry;
import megane6.weplanet.domain.entity.event.HashtagEventTarget;
import megane6.weplanet.repository.fan.PostRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.event.HashtagEventEntryRepository;
import megane6.weplanet.repository.event.HashtagEventTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 팬 게시글 1개를 해시태그 총공 집계에 기록
 * 해시태그가 들어간 글이면 "인정" 또는 "제외(사유)"로 한 줄 남기고, 해시태그가 없으면 아무것도 안 남긴다.
 */
@Service
@RequiredArgsConstructor
public class HashtagEventEntryService {
	
	public static final int DAILY_LIMIT = 3;	// 1인 1일 인정 건수
	
	private final PostRepository pr;
	private final CommunityMemberRepository cmr;
	private final HashtagEventTargetRepository hetr;
	private final HashtagEventEntryRepository heer;
	
	/**
	 * REQUIRES_NEW : 항상 "새 트랜잭션"으로 실행한다.
	 * 리스너는 글 저장 트랜잭션이 커밋된 "뒤"에 불리는데, 그 시점엔 기존 트랜잭션에 더 쓸 수 없어서
	 * 새로 열어야 이 안의 save가 실제로 DB에 반영된다.
	 */
	
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFanPost(Long postId) {
		Post post = pr.findById(postId).orElse(null);
		if (post == null
		|| post.getBoardType() != BoardType.FAN
		|| post.getArtist() == null
		|| post.getAuthor() == null) {
			return;
		}
		
		if (heer.existsByPost_Id(postId)) {
			return;
		}
		
		// 1) 글 쓴 시각에 이 커뮤니티가 참여 중인 총공이 있는지
		Optional<HashtagEventTarget> ongoing =
				hetr.findOngoingTarget(post.getArtist().getId(), post.getCreatedAt());
		if (ongoing.isEmpty()) {
			return;
		}
		
		HashtagEventTarget target = ongoing.get();
		
		// 2) 본문에 그 해시태그가 들어 있는지 (없으면 총공과 상관없는 평범한 글)
		if (!HashtagMatcher.contains(post.getContent(), target.getHashtag())) {
			return;
		}
		
		// 3) 인정할지, 어떤 사유로 제외할지
		HashtagEntryStatus status = judge(post, target);
		heer.save(HashtagEventEntry.record(target, post, status));
	}
	
	// 확인 순서가 곧 우선순위: Hide 글 → 미가입 → 오늘 3건 초과 → 인정
	private HashtagEntryStatus judge(Post post, HashtagEventTarget target) {
		Long fanId = post.getAuthor().getId();
		Long artistId = post.getArtist().getId();
		
		if (post.isHiddenFromArtist()) {
			return HashtagEntryStatus.HIDDEN_FROM_ARTIST;
		}
		
		if (!cmr.existsByFanIdAndArtistId(fanId, artistId)) {
			return HashtagEntryStatus.NOT_MEMBER;
		}
		
		// "하루" = 글 쓴 날짜의 00:00 이상 ~ 다음 날 00:00 미만
		LocalDate day = post.getCreatedAt().toLocalDate();
		long countedToday = heer.countFanEntries(
				target.getId(),
				fanId,
				HashtagEntryStatus.COUNTED,
				day.atStartOfDay(),
				day.plusDays(1).atStartOfDay()
		);
		
		if (countedToday >= DAILY_LIMIT) {
			return HashtagEntryStatus.DAILY_LIMIT;
		}
		
		return HashtagEntryStatus.COUNTED;
	}
}
