package megane6.weplanet.service.event;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.event.HashtagEventDashboard;
import megane6.weplanet.domain.dto.event.HashtagEventPageView;
import megane6.weplanet.domain.dto.event.HashtagHomeBanner;
import megane6.weplanet.domain.dto.event.HashtagRankingRow;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.event.HashtagEvent;
import megane6.weplanet.i18n.Messages;
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

/** 팬 공개 이벤트 페이지 (순위는 관리자 모니터링과 같은 계산). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HashtagEventPageService {
	
	private static final int TOP_COUNT = 3;
	private static final int BANNER_DAYS_AFTER_FINALIZE = 7;  // 확정 후 홈 배너 유지 기간
	
	private final HashtagEventRepository her;
	private final HashtagEventStatsService hess;
	private final CommunityMemberRepository cmr;
	private final Messages messages;
	
	// 대표 이벤트: 진행 중(또는 최근 종료) → 없으면 다음 예정
	public Optional<HashtagEventPageView> getFeatured(Long viewerId) {
		LocalDateTime now = LocalDateTime.now();
		
		return her.findFirstByStartAtLessThanEqualOrderByStartAtDesc(now)
				.or(() -> her.findFirstByStartAtAfterOrderByStartAtAsc(now))
				.map(event -> toView(event, viewerId, now));
	}
	
	// 홈 슬라이드 (대표 이벤트가 없거나 확정 후 7일이 지나면 숨김)
	public Optional<HashtagHomeBanner> getHomeBanner() {
		LocalDateTime weekAgo = LocalDateTime.now().minusDays(BANNER_DAYS_AFTER_FINALIZE);
		
		return getFeatured(null)
				.filter(view -> view.dashboard().finalizedAt() == null
						|| view.dashboard().finalizedAt().isAfter(weekAgo))
				.map(view -> HashtagHomeBanner.from(view, messages));
	}
	
	// 결과 공지 링크로 온 회차 이벤트
	public HashtagEventPageView getEvent(Long eventId, Long viewerId) {
		HashtagEvent event = her.findById(eventId).orElseThrow(() ->
				new IllegalArgumentException("hashtagEvent.error.notFound"));
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
	
	// 가입한 모든 커뮤니티
	private Set<Long> myCommunityIds(Long viewerId) {
		if (viewerId == null) {
			return Set.of();
		}
		
		return cmr.findByFanId(viewerId)
				.stream()
				.map(CommunityMember::getArtistId)
				.collect(Collectors.toSet());
	}
	
	// 종료(또는 시작)까지 남은 시간 문구
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
		return days > 0
				? messages.get("hashtagEvent.remainingWithDays", days, left.toHoursPart(), left.toMinutesPart())
				: messages.get("hashtagEvent.remaining", left.toHoursPart(), left.toMinutesPart());
	}
}