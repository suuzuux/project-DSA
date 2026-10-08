package megane6.weplanet.service.event;

import megane6.weplanet.exception.LocalizedIllegalArgumentException;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.event.*;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AdminActionType;
import megane6.weplanet.domain.entity.enumfolder.AdminTargetType;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.domain.entity.event.HashtagEvent;
import megane6.weplanet.domain.entity.event.HashtagEventTarget;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.event.HashtagEventRepository;
import megane6.weplanet.repository.event.HashtagEventTargetRepository;
import megane6.weplanet.service.admin.AdminActionLogService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 관리자 해시태그 총공 목록·등록·수정·삭제·참여 아티스트 검색 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HashtagEventAdminService {
	
	private static final int SEARCH_LIMIT = 20;  // 검색 결과 최대 20팀
	private static final int MIN_TARGETS = 2;    // 최소 2팀
	
	private final HashtagEventRepository her;
	private final HashtagEventTargetRepository hetr;
	private final UserRepository ur;
	private final AdminActionLogService als;
	private final HashtagEventStatsService hess;
	
	public List<HashtagEventListItem> getEvents() {
		LocalDateTime now = LocalDateTime.now();
		
		return her.findAllByOrderByStartAtDesc()
				.stream()
				.map(event -> HashtagEventListItem.of(event, now))
				.toList();
	}
	
	// 수정 폼 값 (LAZY targets 를 트랜잭션 안에서 폼으로 옮김)
	public HashtagEventForm getEditForm(Long eventId) {
		HashtagEvent event = requireEvent(eventId);
		event.requireEditable(LocalDateTime.now());
		
		HashtagEventForm form = new HashtagEventForm();
		form.setTitle(event.getTitle());
		form.setStartDate(event.getStartAt().toLocalDate());
		form.setEndDate(event.getEndAt().toLocalDate());
		
		for (HashtagEventTarget target : event.getTargets()) {
			form.getArtistIds().add(target.getArtist().getId());
			form.getHashtags().add(target.getHashtag());
		}
		
		return form;
	}
	
	public List<HashtagArtistOption> searchArtists(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return List.of();
		}
		
		return hetr.searchArtistOptions(
				Role.ARTIST,
				UserStatus.ACTIVE,
				keyword.strip(),
				PageRequest.of(0, SEARCH_LIMIT)
		);
	}
	
	// 오류로 폼을 다시 보여줄 때도 참여 아티스트 입력값을 유지한다.
	public List<HashtagEventTargetRow> toTargetRows(HashtagEventForm form) {
		if (form.getArtistIds().isEmpty()) {
			return List.of();
		}
		
		Map<Long, HashtagArtistOption> options = hetr.findArtistOptionsByIds(form.getArtistIds())
				.stream()
				.collect(Collectors.toMap(HashtagArtistOption::artistId, Function.identity()));
		
		List<HashtagEventTargetRow> rows = new ArrayList<>();
		for (int i = 0; i < form.getArtistIds().size(); i++) {
			HashtagArtistOption option = options.get(form.getArtistIds().get(i));
			if (option != null) {
				rows.add(new HashtagEventTargetRow(option, form.hashtagAt(i)));
			}
		}
		
		return rows;
	}
	
	@Transactional
	public Long create(User admin, HashtagEventForm form, String ipAddress) {
		HashtagEvent event = HashtagEvent.create(
				form.getTitle(), form.getStartDate(), form.getEndDate(), admin);
		
		validateSchedule(event, null, LocalDateTime.now());
		applyTargets(event, form);
		her.save(event);
		
		als.recordAction(
				admin.getId(),
				AdminActionType.HASHTAG_EVENT_CREATE,
				AdminTargetType.HASHTAG_EVENT,
				event.getId(),
				event.getTitle() + " 등록 (" + event.getTargets().size() + "팀)",
				ipAddress
		);
		
		return event.getId();
	}
	
	@Transactional
	public void update(Long eventId, User admin, HashtagEventForm form, String ipAddress) {
		HashtagEvent event = requireEvent(eventId);
		LocalDateTime now = LocalDateTime.now();
		
		// 예외가 나면 롤백되어 바꾼 값도 반영되지 않는다.
		event.update(form.getTitle(), form.getStartDate(), form.getEndDate(), now);
		validateSchedule(event, eventId, now);
		applyTargets(event, form);
		
		als.recordAction(
				admin.getId(),
				AdminActionType.HASHTAG_EVENT_UPDATE,
				AdminTargetType.HASHTAG_EVENT,
				eventId,
				event.getTitle() + " 수정 (" + event.getTargets().size() + "팀)",
				ipAddress
		);
	}
	
	@Transactional
	public void delete(Long eventId, User admin, String ipAddress) {
		HashtagEvent event = requireEvent(eventId);
		event.requireEditable(LocalDateTime.now());
		
		String title = event.getTitle();
		her.delete(event);  // 참여 아티스트는 cascade 로 함께 삭제
		
		als.recordAction(
				admin.getId(),
				AdminActionType.HASHTAG_EVENT_DELETE,
				AdminTargetType.HASHTAG_EVENT,
				eventId,
				title + " 삭제",
				ipAddress
		);
	}
	
	public HashtagEventDashboard getDashboard(Long eventId) {
		return hess.getDashboard(requireEvent(eventId));
	}
	
	// 집계 확정 - 현재 순위를 final_* 에 고정한다.
	@Transactional
	public void finalizeEvent(Long eventId, User admin, String ipAddress) {
		HashtagEvent event = requireEvent(eventId);
		event.finalizeResult(LocalDateTime.now());
		
		List<HashtagRankingRow> ranking = hess.calculateLiveRanking(eventId);
		Map<Long, HashtagEventTarget> targetsById = event.getTargets()
				.stream()
				.collect(Collectors.toMap(HashtagEventTarget::getId, Function.identity()));
		
		for (HashtagRankingRow row : ranking) {
			targetsById.get(row.targetId()).recordFinalResult(
					(int) row.memberCount(),
					(int) row.participantCount(),
					(int) row.postCount(),
					row.rank()
			);
		}
		
		String winner = ranking.isEmpty()
				? ""
				: " / 1위 " + ranking.get(0).artistName() + " " + ranking.get(0).participationRate() + "%";
		als.recordAction(
				admin.getId(),
				AdminActionType.HASHTAG_EVENT_FINALIZE,
				AdminTargetType.HASHTAG_EVENT,
				eventId,
				event.getTitle() + " 집계 확정" + winner,
				ipAddress
		);
	}
	
	// 결과 공지 초안 (확정된 숫자로만)
	public HashtagResultNoticeDraft buildResultNotice(Long eventId) {
		HashtagEvent event = requireEvent(eventId);
		if (event.getFinalizedAt() == null) {
			throw new IllegalStateException("adminHashtag.error.notFinalized");
		}
		
		return HashtagResultNoticeDraft.from(hess.getDashboard(event));
	}
	
	public HashtagEvent requireEvent(Long eventId) {
		return her.findById(eventId).orElseThrow(() ->
				new IllegalArgumentException("hashtagEvent.error.notFound"));
	}
	
	// 시작일은 오늘 이후, 다른 이벤트와 기간이 겹치면 안 된다 (오늘 시작 허용).
	private void validateSchedule(HashtagEvent event, Long selfId, LocalDateTime now) {
		if (event.getStartAt().toLocalDate().isBefore(now.toLocalDate())) {
			throw new IllegalArgumentException("adminHashtag.error.startInPast");
		}
		
		boolean overlapped = (selfId == null)
				? her.existsByStartAtLessThanEqualAndEndAtGreaterThanEqual(
				event.getEndAt(), event.getStartAt())
				: her.existsByStartAtLessThanEqualAndEndAtGreaterThanEqualAndIdNot(
				event.getEndAt(), event.getStartAt(), selfId);
		
		if (overlapped) {
			throw new IllegalArgumentException(
					"adminHashtag.error.overlap");
		}
	}
	
	private void applyTargets(HashtagEvent event, HashtagEventForm form) {
		List<Long> artistIds = form.getArtistIds();
		
		if (artistIds.size() < MIN_TARGETS) {
			throw new LocalizedIllegalArgumentException("adminHashtag.error.minTargets", MIN_TARGETS);
		}
		if (new HashSet<>(artistIds).size() != artistIds.size()) {
			throw new IllegalArgumentException("adminHashtag.error.duplicateArtist");
		}
		
		Map<Long, User> artists = ur.findAllById(artistIds)
				.stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		
		// 빠진 아티스트를 지우고 나머지는 추가하거나 해시태그만 고친다.
		event.retainTargets(artistIds);
		
		Set<String> usedHashtags = new HashSet<>();
		for (int i = 0; i < artistIds.size(); i++) {
			User artist = artists.get(artistIds.get(i));
			if (artist == null || artist.getRole() != Role.ARTIST) {
				throw new LocalizedIllegalArgumentException("adminHashtag.error.artistNotFound", String.valueOf(artistIds.get(i)));
			}
			
			String rawHashtag = form.hashtagAt(i);
			if (rawHashtag == null || rawHashtag.isBlank()) {
				throw new LocalizedIllegalArgumentException("adminHashtag.error.hashtagRequired", artist.getNickname());
			}
			
			String hashtag = HashtagEventTarget.normalize(rawHashtag);
			// 대소문자만 다른 태그는 같은 태그로 본다.
			if (!usedHashtags.add(hashtag.toLowerCase(Locale.ROOT))) {
				throw new LocalizedIllegalArgumentException("adminHashtag.error.duplicateHashtag", hashtag);
			}
			
			event.putTarget(artist, hashtag);
		}
	}
}