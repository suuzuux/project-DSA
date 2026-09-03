package megane6.weplanet.service.portal;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.calendar.CalendarDayView;
import megane6.weplanet.domain.dto.calendar.ScheduleEventView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.calendar.ScheduleCategory;
import megane6.weplanet.domain.entity.portal.ArtistProfile;
import megane6.weplanet.domain.entity.calendar.ArtistSchedule;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.MembershipRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.media.BoardMediaRepository;
import megane6.weplanet.repository.portal.ArtistProfileRepository;
import megane6.weplanet.repository.calendar.ArtistScheduleRepository;
import megane6.weplanet.repository.portal.PortalNoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.Year;
import java.time.YearMonth;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PortalManagementService {

    private final PortalNoticeRepository portalNoticeRepository;
    private final ArtistScheduleRepository artistScheduleRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final BoardMediaRepository boardMediaRepository;
    private final MembershipRepository membershipRepository;
    private final ReportRepository reportRepository;
    private final CommentReportRepository commentReportRepository;

    public static final int MAX_PINNED = 5;

    @Transactional(readOnly = true)
    public List<PortalNotice> getNotices(User artist) {
        return portalNoticeRepository.findByArtistOrderByPinnedDescPinOrderAscCreatedAtDesc(artist);
    }

    @Transactional(readOnly = true)
    public List<PortalNotice> getPublishedNotices(User artist) {
        return portalNoticeRepository.findByArtistAndPublishedTrueOrderByPinnedDescPinOrderAscCreatedAtDesc(artist);
    }

    @Transactional(readOnly = true)
    public PortalNotice getNotice(User artist, Long noticeId) {
        return portalNoticeRepository.findByIdAndArtist(noticeId, artist)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PortalNotice getPublishedNotice(User artist, Long noticeId) {
        PortalNotice notice = getNotice(artist, noticeId);
        if (!notice.isPublished()) {
            throw new IllegalArgumentException("공지를 찾을 수 없습니다.");
        }
        return notice;
    }

    public PortalNotice saveNotice(User artist, Long noticeId, String title, String content, boolean published, boolean pinned) {
        validateText(title, "제목을 입력해주세요.");
        validateText(content, "본문을 입력해주세요.");
        PortalNotice notice = noticeId == null
                ? PortalNotice.create(artist, title.trim(), content.trim(), published)
                : getNotice(artist, noticeId);
        if (noticeId != null) {
            notice.update(title, content, published);
        }
        applyPinState(artist, notice, pinned);
        return portalNoticeRepository.save(notice);
    }

    public void reorderPinned(User artist, List<Long> ids) {
        List<PortalNotice> pinned = portalNoticeRepository.findByArtistAndPinnedTrueOrderByPinOrderAsc(artist);
        if (ids == null || ids.isEmpty() || ids.size() != pinned.size()) {
            throw new IllegalArgumentException("상단 노출 공지 순서가 올바르지 않습니다.");
        }
        Map<Long, PortalNotice> byId = pinned.stream()
                .collect(Collectors.toMap(PortalNotice::getId, item -> item));
        List<PortalNotice> reordered = new ArrayList<>(ids.size());
        int order = 1;
        for (Long id : ids) {
            PortalNotice notice = byId.remove(id);
            if (notice == null) {
                throw new IllegalArgumentException("상단 노출 공지 순서가 올바르지 않습니다.");
            }
            notice.applyPin(true, order++);
            reordered.add(notice);
        }
        if (!byId.isEmpty()) {
            throw new IllegalArgumentException("상단 노출 공지 순서가 올바르지 않습니다.");
        }
        portalNoticeRepository.saveAll(reordered);
    }

    @Transactional(readOnly = true)
    public long countPinned(User artist) {
        return portalNoticeRepository.countByArtistAndPinnedTrue(artist);
    }

    public void deleteNotice(User artist, Long noticeId) {
        portalNoticeRepository.delete(getNotice(artist, noticeId));
        compactPinOrder(artist);
    }

    private void applyPinState(User artist, PortalNotice notice, boolean pinned) {
        boolean wasPinned = notice.isPinned();
        if (pinned) {
            long count = portalNoticeRepository.countByArtistAndPinnedTrue(artist);
            if (!wasPinned && count >= MAX_PINNED) {
                throw new IllegalArgumentException("상단 노출은 최대 5개까지 가능합니다.");
            }
            if (!wasPinned) {
                List<PortalNotice> current = portalNoticeRepository.findByArtistAndPinnedTrueOrderByPinOrderAsc(artist);
                int shift = 2;
                for (PortalNotice item : current) {
                    item.applyPin(true, shift++);
                }
                notice.applyPin(true, 1);
            }
            return;
        }
        if (wasPinned) {
            notice.applyPin(false, null);
            compactPinOrder(artist);
        }
    }

    private void compactPinOrder(User artist) {
        List<PortalNotice> pinned = portalNoticeRepository.findByArtistAndPinnedTrueOrderByPinOrderAsc(artist);
        int order = 1;
        for (PortalNotice item : pinned) {
            item.applyPin(true, order++);
        }
        if (!pinned.isEmpty()) {
            portalNoticeRepository.saveAll(pinned);
        }
    }

    private static final int BIRTHDAY_EXPAND_YEARS_AHEAD = 5;

    @Transactional(readOnly = true)
    public List<ArtistSchedule> getSchedules(User artist) {
        return artistScheduleRepository.findByArtistOrderByScheduleAtAsc(artist);
    }

    @Transactional(readOnly = true)
    public List<CalendarDayView> getMonthGrid(User artist, YearMonth month) {
        List<ScheduleEventView> schedules = getScheduleEventsInMonth(artist, month);
        Map<LocalDate, List<ScheduleEventView>> byDate = schedules.stream()
                .collect(Collectors.groupingBy(
                        item -> LocalDate.parse(item.date()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        LocalDate first = month.atDay(1);
        int leading = first.getDayOfWeek().getValue() % 7; // Sunday = 0
        LocalDate cursor = first.minusDays(leading);
        LocalDate today = LocalDate.now();
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;

        List<CalendarDayView> cells = new ArrayList<>(42);
        for (int i = 0; i < 42; i++) {
            LocalDate date = cursor.plusDays(i);
            cells.add(new CalendarDayView(
                    date.getDayOfMonth(),
                    date.format(iso),
                    YearMonth.from(date).equals(month),
                    date.equals(today),
                    byDate.getOrDefault(date, List.of())
            ));
        }
        return cells;
    }

    public void createSchedule(User artist, ScheduleCategory category, String title, String description,
                               String location, String ticketUrl, LocalDateTime scheduleAt) {
        ScheduleCategory resolved = category == null ? ScheduleCategory.OTHER : category;
        if (scheduleAt == null) {
            throw new IllegalArgumentException(resolved == ScheduleCategory.BIRTHDAY
                    ? "생일 날짜를 입력해주세요."
                    : "일정 일시를 입력해주세요.");
        }
        if (resolved == ScheduleCategory.BIRTHDAY) {
            LocalDate birthDate = scheduleAt.toLocalDate();
            if (birthDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("생일은 오늘 이전 날짜만 등록할 수 있습니다.");
            }
            scheduleAt = birthDate.atTime(LocalTime.MIDNIGHT);
            if (title == null || title.isBlank()) {
                title = artist.getNickname() + " 생일";
            }
        }
        validateText(title, "일정 제목을 입력해주세요.");
        artistScheduleRepository.save(ArtistSchedule.create(
                artist,
                resolved,
                title,
                description,
                location,
                ticketUrl,
                scheduleAt
        ));
    }

    @Transactional(readOnly = true)
    public List<ScheduleEventView> getPublicScheduleEvents() {
        return expandScheduleEvents(artistScheduleRepository.findAllByOrderByScheduleAtAsc());
    }

    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> getPublicEventsByDate() {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (ScheduleEventView event : getPublicScheduleEvents()) {
            grouped.computeIfAbsent(event.date(), key -> new ArrayList<>()).add(toCalendarEvent(event));
        }
        return grouped;
    }

    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> getPublicEventsByDateForArtists(java.util.Collection<Long> artistIds) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        if (artistIds == null || artistIds.isEmpty()) {
            return grouped;
        }
        List<ScheduleEventView> events = expandScheduleEvents(
                artistScheduleRepository.findByArtistIdInOrderByScheduleAtAsc(artistIds));
        for (ScheduleEventView event : events) {
            grouped.computeIfAbsent(event.date(), key -> new ArrayList<>()).add(toCalendarEvent(event));
        }
        return grouped;
    }

    private List<ScheduleEventView> getScheduleEventsInMonth(User artist, YearMonth month) {
        if (artist == null) {
            return List.of();
        }
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59);

        List<ScheduleEventView> events = new ArrayList<>();
        artistScheduleRepository.findByArtistAndScheduleAtBetweenOrderByScheduleAtAsc(artist, start, end).stream()
                .filter(item -> item.getCategory() != ScheduleCategory.BIRTHDAY)
                .map(ScheduleEventView::from)
                .forEach(events::add);

        appendBirthdayEventsInMonth(events, artist, month);
        events.sort(Comparator.comparing(ScheduleEventView::date).thenComparing(ScheduleEventView::time));
        return events;
    }

    private List<ScheduleEventView> expandScheduleEvents(List<ArtistSchedule> schedules) {
        int maxYear = Year.now().getValue() + BIRTHDAY_EXPAND_YEARS_AHEAD;
        List<ScheduleEventView> events = new ArrayList<>();
        for (ArtistSchedule schedule : schedules) {
            if (schedule.getCategory() == ScheduleCategory.BIRTHDAY) {
                appendBirthdayEvents(events, schedule, schedule.getScheduleAt().toLocalDate().getYear(), maxYear);
                continue;
            }
            events.add(ScheduleEventView.from(schedule));
        }
        events.sort(Comparator.comparing(ScheduleEventView::date).thenComparing(ScheduleEventView::time));
        return events;
    }

    private void appendBirthdayEventsInMonth(List<ScheduleEventView> events, User artist, YearMonth month) {
        List<ArtistSchedule> birthdays = artistScheduleRepository
                .findByArtistAndCategoryOrderByScheduleAtAsc(artist, ScheduleCategory.BIRTHDAY);
        int year = month.getYear();
        for (ArtistSchedule birthday : birthdays) {
            LocalDate birthDate = birthday.getScheduleAt().toLocalDate();
            if (year < birthDate.getYear()) {
                continue;
            }
            LocalDate occurrence = birthdayDateInYear(birthDate, year);
            if (!YearMonth.from(occurrence).equals(month)) {
                continue;
            }
            events.add(ScheduleEventView.from(
                    birthday,
                    occurrence.atTime(birthday.getScheduleAt().toLocalTime())
            ));
        }
    }

    private void appendBirthdayEvents(List<ScheduleEventView> events, ArtistSchedule birthday, int fromYear, int toYear) {
        LocalDate birthDate = birthday.getScheduleAt().toLocalDate();
        LocalTime time = birthday.getScheduleAt().toLocalTime();
        int startYear = Math.max(fromYear, birthDate.getYear());
        for (int year = startYear; year <= toYear; year++) {
            LocalDate occurrence = birthdayDateInYear(birthDate, year);
            events.add(ScheduleEventView.from(birthday, occurrence.atTime(time)));
        }
    }

    private LocalDate birthdayDateInYear(LocalDate birthDate, int year) {
        MonthDay monthDay = MonthDay.from(birthDate);
        try {
            return monthDay.atYear(year);
        } catch (DateTimeException ex) {
            return LocalDate.of(year, 2, 28);
        }
    }

    private Map<String, Object> toCalendarEvent(ScheduleEventView event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "sch-" + event.id());
        item.put("artist", String.valueOf(event.artistId()));
        item.put("type", event.type());
        item.put("time", event.time());
        item.put("place", event.location() == null ? "" : event.location());
        item.put("link", event.ticketUrl());
        item.put("hasTicketImage", event.ticketUrl() != null && !event.ticketUrl().isBlank());
        item.put("title", event.localizedTitle());
        return item;
    }

    public void deleteSchedule(User artist, Long scheduleId) {
        ArtistSchedule schedule = artistScheduleRepository.findById(scheduleId)
                .filter(item -> item.getArtist().getId().equals(artist.getId()))
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));
        artistScheduleRepository.delete(schedule);
    }

    public void rescheduleSchedule(User artist, Long scheduleId, LocalDate targetDate) {
        if (targetDate == null) {
            throw new IllegalArgumentException("옮길 날짜를 입력해주세요.");
        }
        ArtistSchedule schedule = artistScheduleRepository.findById(scheduleId)
                .filter(item -> item.getArtist().getId().equals(artist.getId()))
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));

        LocalDateTime current = schedule.getScheduleAt();
        LocalDate currentDate = current.toLocalDate();
        if (currentDate.equals(targetDate)) {
            return;
        }

        LocalDate newDate;
        if (schedule.getCategory() == ScheduleCategory.BIRTHDAY) {
            int birthYear = currentDate.getYear();
            MonthDay targetMonthDay = MonthDay.from(targetDate);
            try {
                newDate = targetMonthDay.atYear(birthYear);
            } catch (DateTimeException ex) {
                newDate = LocalDate.of(birthYear, 2, 28);
            }
            if (newDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("생일은 오늘 이전 날짜만 등록할 수 있습니다.");
            }
        } else {
            newDate = targetDate;
        }

        schedule.update(
                schedule.getCategory(),
                schedule.getTitle(),
                schedule.getDescription(),
                schedule.getLocation(),
                schedule.getTicketUrl(),
                newDate.atTime(current.toLocalTime())
        );
        artistScheduleRepository.save(schedule);
    }

    public ArtistProfile getOrCreateProfile(User artist) {
        return artistProfileRepository.findByArtist(artist)
                .orElseGet(() -> artistProfileRepository.save(ArtistProfile.create(artist)));
    }

    public void updateProfile(User artist, String nickname, String email, String intro, String headerImageUrl, String logoImageUrl) {
        validateText(nickname, "표시 이름을 입력해주세요.");
        validateText(email, "이메일을 입력해주세요.");

        artist.changePortalProfile(nickname.trim(), email.trim());
        ArtistProfile profile = getOrCreateProfile(artist);
        profile.update(intro, headerImageUrl, logoImageUrl);
        artistProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public long countMemberships(User artist) {
        return membershipRepository.countByArtist(artist);
    }

    @Transactional(readOnly = true)
    public long countNotices(User artist) {
        return portalNoticeRepository.countByArtist(artist);
    }

    @Transactional(readOnly = true)
    public long countUpcomingSchedules(User artist) {
        return artistScheduleRepository.countByArtistAndScheduleAtAfter(artist, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public long countMedia(User artist) {
        return boardMediaRepository.countByGroupIdAndDeletedAtIsNull(artist.getId());
    }

    @Transactional(readOnly = true)
    public long countPendingReports(User artist) {
        return reportRepository.countByPost_Artist(artist) + commentReportRepository.countByComment_Post_Artist(artist);
    }

    private void validateText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
