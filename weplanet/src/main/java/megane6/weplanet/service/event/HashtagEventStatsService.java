package megane6.weplanet.service.event;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.dto.event.HashtagCount;
import megane6.weplanet.domain.dto.event.HashtagDailyCount;
import megane6.weplanet.domain.dto.event.HashtagEventDashboard;
import megane6.weplanet.domain.dto.event.HashtagRankingRow;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import megane6.weplanet.domain.entity.enumfolder.events.HashtagEntryStatus;
import megane6.weplanet.domain.entity.event.HashtagEvent;
import megane6.weplanet.domain.entity.event.HashtagEventTarget;
import megane6.weplanet.repository.ArtistAccountProfileRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.event.HashtagEventEntryRepository;
import megane6.weplanet.repository.event.HashtagEventTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 해시태그 총공 순위·통계 계산. (관리자 모니터링 · 집계 확정 · 공개 이벤트 페이지 공통)
 *
 * 순위 기준 : 참여율 높은 순 → (같으면) 참여 인원 많은 순 → 글 수 많은 순 → 이름순
 * 집계 확정 전에는 매번 새로 계산하고, 확정 후에는 hashtag_event_target.final_* 에 고정된 값을 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HashtagEventStatsService {
	
	private final HashtagEventTargetRepository hetr;
	private final HashtagEventEntryRepository heer;
	private final CommunityMemberRepository cmr;
	private final ArtistAccountProfileRepository aapr;
	
	public HashtagEventDashboard getDashboard(HashtagEvent event) {
		LocalDateTime now = LocalDateTime.now();
		
		List<HashtagRankingRow> ranking = (event.getFinalizedAt() != null)
				? finalRanking(event.getId())
				: calculateLiveRanking(event.getId());
		
		return new HashtagEventDashboard(
				event.getId(),
				event.getTitle(),
				event.getStartAt(),
				event.getEndAt(),
				event.getFinalizedAt(),
				event.statusAt(now),
				ranking,
				excludedCounts(event.getId()),
				dailyCounts(event, now)
		);
	}
	
	// 지금 이 순간 기준의 순위 (확정 전 화면 + "집계 확정" 할 때 고정할 값)
	public List<HashtagRankingRow> calculateLiveRanking(Long eventId) {
		List<HashtagEventTarget> targets = hetr.findByEvent_IdOrderByIdAsc(eventId);
		if (targets.isEmpty()) {
			return List.of();
		}
		
		List<Long> artistIds = targets.stream()
				.map(target -> target.getArtist().getId())
				.toList();
		
		// 쿼리를 참여팀마다 날리지 않고, "group by" 로 팀별 숫자를 한 번에 가져와 Map 으로 바꿔둔다
		Map<Long, Long> memberCounts = cmr.countMembersByArtistIds(artistIds)
				.stream()
				.collect(Collectors.toMap(ArtistCount::artistId, ArtistCount::count));
		Map<Long, Long> participantCounts = toMap(
				heer.countParticipantsByTarget(eventId, HashtagEntryStatus.COUNTED));
		Map<Long, Long> postCounts = toMap(
				heer.countPostsByTarget(eventId, HashtagEntryStatus.COUNTED));
		Map<Long, String> profileImgs = profileImages(artistIds);
		
		List<HashtagRankingRow> rows = new ArrayList<>();
		for (HashtagEventTarget target : targets) {
			Long artistId = target.getArtist().getId();
			rows.add(new HashtagRankingRow(
					target.getId(),
					artistId,
					target.getArtist().getNickname(),
					profileImgs.get(artistId),
					target.getHashtag(),
					memberCounts.getOrDefault(artistId, 0L),
					participantCounts.getOrDefault(target.getId(), 0L),
					postCounts.getOrDefault(target.getId(), 0L),
					0  // 순위는 아래에서 정렬한 뒤 매긴다
			));
		}
		
		rows.sort(Comparator
				.comparingDouble(HashtagRankingRow::participationRate).reversed()
				.thenComparing(Comparator.comparingLong(HashtagRankingRow::participantCount).reversed())
				.thenComparing(Comparator.comparingLong(HashtagRankingRow::postCount).reversed())
				.thenComparing(HashtagRankingRow::artistName));
		
		List<HashtagRankingRow> ranked = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			ranked.add(rows.get(i).withRank(i + 1));
		}
		return ranked;
	}
	
	// 집계 확정된 이벤트: 확정 순간 저장해둔 숫자 그대로
	private List<HashtagRankingRow> finalRanking(Long eventId) {
		List<HashtagEventTarget> targets = hetr.findByEvent_IdOrderByIdAsc(eventId);
		Map<Long, String> profileImgs = profileImages(
				targets.stream().map(target -> target.getArtist().getId()).toList());
		
		return targets.stream()
				.map(target -> new HashtagRankingRow(
						target.getId(),
						target.getArtist().getId(),
						target.getArtist().getNickname(),
						profileImgs.get(target.getArtist().getId()),
						target.getHashtag(),
						valueOf(target.getFinalMemberCount()),
						valueOf(target.getFinalParticipantCount()),
						valueOf(target.getFinalPostCount()),
						target.getFinalRank() == null ? 0 : target.getFinalRank()
				))
				.sorted(Comparator.comparingInt(HashtagRankingRow::rank))
				.toList();
	}
	
	// 인정되지 않은 글의 사유별 개수. EnumMap 은 enum 선언 순서대로 꺼내져서 화면 순서가 일정하다
	private Map<HashtagEntryStatus, Long> excludedCounts(Long eventId) {
		Map<HashtagEntryStatus, Long> counts = new EnumMap<>(HashtagEntryStatus.class);
		for (HashtagCount<HashtagEntryStatus> row : heer.countByStatus(eventId)) {
			if (!row.key().isCounted()) {
				counts.put(row.key(), row.count());
			}
		}
		return counts;
	}
	
	// 시작일부터 (종료일과 오늘 중 빠른 날)까지 하루씩, 인정된 글 수
	private List<HashtagDailyCount> dailyCounts(HashtagEvent event, LocalDateTime now) {
		LocalDate first = event.getStartAt().toLocalDate();
		LocalDate last = event.getEndAt().toLocalDate();
		if (now.toLocalDate().isBefore(last)) {
			last = now.toLocalDate();
		}
		if (last.isBefore(first)) {
			return List.of();  // 아직 시작 전
		}
		
		Map<LocalDate, Long> perDay = heer.findCreatedAts(event.getId(), HashtagEntryStatus.COUNTED)
				.stream()
				.collect(Collectors.groupingBy(LocalDateTime::toLocalDate, Collectors.counting()));
		
		long max = 1;  // 0 으로 나누지 않게 최소 1
		for (Long count : perDay.values()) {
			max = Math.max(max, count);
		}
		
		List<HashtagDailyCount> days = new ArrayList<>();
		for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
			long count = perDay.getOrDefault(day, 0L);
			days.add(new HashtagDailyCount(day, count, (int) (count * 100 / max)));
		}
		return days;
	}
	
	private Map<Long, String> profileImages(List<Long> artistIds) {
		if (artistIds.isEmpty()) {
			return Map.of();
		}
		
		// 프로필 이미지가 null 인 아티스트도 있어서 Collectors.toMap 대신 직접 담는다 (toMap 은 null 값을 못 넣음)
		Map<Long, String> images = new HashMap<>();
		for (ArtistAccountProfile profile : aapr.findAllByUserIds(artistIds)) {
			images.put(profile.getUserId(), profile.getProfileImg());
		}
		return images;
	}
	
	private Map<Long, Long> toMap(List<HashtagCount<Long>> rows) {
		return rows.stream().collect(Collectors.toMap(HashtagCount::key, HashtagCount::count));
	}
	
	private long valueOf(Integer value) {
		return value == null ? 0 : value;
	}
}