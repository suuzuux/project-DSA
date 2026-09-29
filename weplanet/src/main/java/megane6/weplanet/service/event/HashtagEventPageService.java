package megane6.weplanet.service.event;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.event.HashtagEventDashboard;
import megane6.weplanet.domain.dto.event.HashtagEventPageView;
import megane6.weplanet.domain.dto.event.HashtagHomeBanner;
import megane6.weplanet.domain.dto.event.HashtagRankingRow;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.event.HashtagEvent;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.event.HashtagEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 팬 공개 이벤트 페이지(/events/hashtag).
 * 순위 계산은 관리자 모니터링과 같은 HashtagEventStatsService 를 그대로 쓰고,
 * 여기서는 "어떤 이벤트를 보여줄지"와 "내 커뮤니티"만 더한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HashtagEventPageService {
	
	private static final int TOP_COUNT = 3;
	private static final int BANNER_DAYS_AFTER_FINALIZE = 7;  // 결과 발표 배너를 홈에 남겨두는 기간
	
	private final HashtagEventRepository her;
	private final HashtagEventStatsService hess;
	private final CommunityMemberRepository cmr;
	
	// 홈 배너로 들어왔을 때: 진행 중(또는 가장 최근에 끝난) 이벤트, 없으면 다음 예정 이벤트
	public Optional<HashtagEventPageView> getFeatured(Long viewerId) {
		LocalDateTime now = LocalDateTime.now();
		
		return her.findFirstByStartAtLessThanEqualOrderByStartAtDesc(now)
				.or(() -> her.findFirstByStartAtAfterOrderByStartAtAsc(now))
				.map(event -> toView(event, viewerId, now));
	}
	
	// 홈 캐러셀 맨 앞 슬라이드. 대표 이벤트가 없거나, 집계 확정 후 7일이 지났으면 배너를 내린다
	public Optional<HashtagHomeBanner> getHomeBanner() {
		LocalDateTime weekAgo = LocalDateTime.now().minusDays(BANNER_DAYS_AFTER_FINALIZE);
		
		return getFeatured(null)
				.filter(view -> view.dashboard().finalizedAt() == null
						|| view.dashboard().finalizedAt().isAfter(weekAgo))
				.map(HashtagHomeBanner::from);
	}
	
	// 결과 공지의 링크로 들어왔을 때: 그 회차 이벤트
	public HashtagEventPageView getEvent(Long eventId, Long viewerId) {
		HashtagEvent event = her.findById(eventId).orElseThrow(() ->
				new IllegalArgumentException("해시태그 총공 이벤트를 찾을 수 없습니다."));
		return toView(event, viewerId, LocalDateTime.now());
	}
	
	private HashtagEventPageView toView(HashtagEvent event, Long viewerId, LocalDateTime now) {
		HashtagEventDashboard dashboard = hess.getDashboard(event);
		Set<Long> myArtistIds = myCommunityIds(viewerId);
		
		List<HashtagRankingRow> myRows = dashboard.ranking()
				.stream()
				.filter(row -> myArtistIds.contains(row.artistId()))
				.toList();
		List<HashtagRankingRow> top = dashboard.ranking()
				.stream()
				.limit(TOP_COUNT)
				.toList();
		
		return new HashtagEventPageView(dashboard, top, myArtistIds, myRows, remainingText(event, now));
	}
	
	// 로그인한 사람이 가입한 모든 커뮤니티 (여러 커뮤니티에 가입했으면 전부 "내 커뮤니티")
	private Set<Long> myCommunityIds(Long viewerId) {
		if (viewerId == null) {
			return Set.of();
		}
		
		return cmr.findByFanId(viewerId)
				.stream()
				.map(CommunityMember::getArtistId)
				.collect(Collectors.toSet());
	}
	
	// 진행 중이면 종료까지, 예정이면 시작까지 남은 시간 → "1일 4시간 12분"
	private String remainingText(HashtagEvent event, LocalDateTime now) {
		LocalDateTime until = switch (event.statusAt(now)) {
			case SCHEDULED -> event.getStartAt();
			case ONGOING -> event.getEndAt();
			default -> null;
		};
		if (until == null) {
			return null;
		}
		
		Duration left = Duration.between(now, until);
		long days = left.toDays();
		return (days > 0 ? days + "일 " : "") + left.toHoursPart() + "시간 " + left.toMinutesPart() + "분";
	}
}