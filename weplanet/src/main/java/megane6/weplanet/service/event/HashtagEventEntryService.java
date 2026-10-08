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

/** 팬 게시글을 총공 집계에 기록 (해시태그가 있으면 인정 또는 제외 사유로 기록). */
@Service
@RequiredArgsConstructor
public class HashtagEventEntryService {
	
	public static final int DAILY_LIMIT = 3;	// 1인 1일 인정 건수
	
	private final PostRepository pr;
	private final CommunityMemberRepository cmr;
	private final HashtagEventTargetRepository hetr;
	private final HashtagEventEntryRepository heer;
	
	/** 리스너가 커밋 뒤에 부르므로 새 트랜잭션으로 저장한다. */
	
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
		
		// 2) 본문에 그 해시태그가 있는지
		if (!HashtagMatcher.contains(post.getContent(), target.getHashtag())) {
			return;
		}
		
		// 3) 인정할지, 어떤 사유로 제외할지
		HashtagEntryStatus status = judge(post, target);
		heer.save(HashtagEventEntry.record(target, post, status));
	}
	
	// 판정 순서: Hide 글 → 미가입 → 하루 3건 초과 → 인정
	private HashtagEntryStatus judge(Post post, HashtagEventTarget target) {
		Long fanId = post.getAuthor().getId();
		Long artistId = post.getArtist().getId();
		
		if (post.isHiddenFromArtist()) {
			return HashtagEntryStatus.HIDDEN_FROM_ARTIST;
		}
		
		if (!cmr.existsByFanIdAndArtistId(fanId, artistId)) {
			return HashtagEntryStatus.NOT_MEMBER;
		}
		
		// 하루 = 작성일 00:00 ~ 다음 날 00:00
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
