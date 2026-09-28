package megane6.weplanet.service.event;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.event.HashtagArtistOption;
import megane6.weplanet.domain.dto.event.HashtagEventForm;
import megane6.weplanet.domain.dto.event.HashtagEventListItem;
import megane6.weplanet.domain.dto.event.HashtagEventTargetRow;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 최고관리자 > 이벤트 > 해시태그 총공 : 이벤트 목록 / 등록 / 수정 / 삭제 / 참여 아티스트 검색
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HashtagEventAdminService {
	
	private static final int SEARCH_LIMIT = 20;  // 검색 결과는 20팀까지만 (아티스트가 수백 명이어도 화면이 안 길어지게)
	private static final int MIN_TARGETS = 2;    // 순위 경쟁이라 최소 2팀
	
	private final HashtagEventRepository her;
	private final HashtagEventTargetRepository hetr;
	private final UserRepository ur;
	private final AdminActionLogService als;
	
	public List<HashtagEventListItem> getEvents() {
		LocalDateTime now = LocalDateTime.now();
		
		return her.findAllByOrderByStartAtDesc()
				.stream()
				.map(event -> HashtagEventListItem.of(event, now))
				.toList();
	}
	
	// 수정 폼에 채울 값. targets 는 LAZY 라서 트랜잭션 안(이 메서드 안)에서 꺼내 폼 객체로 옮겨 담는다
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
	
	// 폼의 "참여 아티스트" 줄들. 입력 오류로 폼을 다시 보여줄 때도 입력했던 값이 그대로 남게 한다
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
		
		// 여기서 예외가 나면 트랜잭션이 롤백돼서, 이미 바꾼 값도 DB에 반영되지 않는다
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
		her.delete(event);  // 참여 아티스트(targets)는 cascade 로 같이 삭제
		
		als.recordAction(
				admin.getId(),
				AdminActionType.HASHTAG_EVENT_DELETE,
				AdminTargetType.HASHTAG_EVENT,
				eventId,
				title + " 삭제",
				ipAddress
		);
	}
	
	public HashtagEvent requireEvent(Long eventId) {
		return her.findById(eventId).orElseThrow(() ->
				new IllegalArgumentException("해시태그 총공 이벤트를 찾을 수 없습니다."));
	}
	
	// 시작일은 오늘 이후 + 다른 이벤트와 기간이 겹치면 안 됨 (동시 진행 1개)
	// 오늘 시작도 허용: 등록하는 순간 바로 "진행 중"이 되고 그때부터 쓴 글이 집계된다 (시연할 때 편함)
	private void validateSchedule(HashtagEvent event, Long selfId, LocalDateTime now) {
		if (event.getStartAt().toLocalDate().isBefore(now.toLocalDate())) {
			throw new IllegalArgumentException("시작일은 오늘 이후로 정해주세요.");
		}
		
		boolean overlapped = (selfId == null)
				? her.existsByStartAtLessThanEqualAndEndAtGreaterThanEqual(
				event.getEndAt(), event.getStartAt())
				: her.existsByStartAtLessThanEqualAndEndAtGreaterThanEqualAndIdNot(
				event.getEndAt(), event.getStartAt(), selfId);
		
		if (overlapped) {
			throw new IllegalArgumentException(
					"기간이 겹치는 다른 해시태그 총공 이벤트가 있습니다. (동시에 1개만 진행할 수 있어요)");
		}
	}
	
	private void applyTargets(HashtagEvent event, HashtagEventForm form) {
		List<Long> artistIds = form.getArtistIds();
		
		if (artistIds.size() < MIN_TARGETS) {
			throw new IllegalArgumentException("참여 아티스트를 " + MIN_TARGETS + "팀 이상 지정해주세요.");
		}
		if (new HashSet<>(artistIds).size() != artistIds.size()) {
			throw new IllegalArgumentException("같은 아티스트가 두 번 들어갔습니다.");
		}
		
		Map<Long, User> artists = ur.findAllById(artistIds)
				.stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		
		// 체크가 풀린 아티스트부터 빼고, 남은/새 아티스트는 putTarget 으로 추가하거나 해시태그만 고친다
		event.retainTargets(artistIds);
		
		Set<String> usedHashtags = new HashSet<>();
		for (int i = 0; i < artistIds.size(); i++) {
			User artist = artists.get(artistIds.get(i));
			if (artist == null || artist.getRole() != Role.ARTIST) {
				throw new IllegalArgumentException("아티스트를 찾을 수 없습니다. (id " + artistIds.get(i) + ")");
			}
			
			String rawHashtag = form.hashtagAt(i);
			if (rawHashtag == null || rawHashtag.isBlank()) {
				throw new IllegalArgumentException(artist.getNickname() + "의 해시태그를 입력해주세요.");
			}
			
			String hashtag = HashtagEventTarget.normalize(rawHashtag);
			// 대소문자만 다른 태그는 같은 태그로 본다 (#STELLA = #stella → 집계할 때도 대소문자 무시)
			if (!usedHashtags.add(hashtag.toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException("다른 아티스트와 해시태그가 겹칩니다: " + hashtag);
			}
			
			event.putTarget(artist, hashtag);
		}
	}
}