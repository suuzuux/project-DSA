package megane6.weplanet.service.portal;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.calendar.CalendarDayView;
import megane6.weplanet.domain.dto.calendar.ScheduleEventView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Gender;
import megane6.weplanet.domain.entity.enumfolder.calendar.ScheduleCategory;
import megane6.weplanet.domain.entity.portal.ArtistProfile;
import megane6.weplanet.domain.entity.calendar.ArtistSchedule;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.repository.comment.CommentReportRepository;
import megane6.weplanet.repository.membership.MembershipRepository;
import megane6.weplanet.repository.fan.ReportRepository;
import megane6.weplanet.repository.live.LiveCommentReportRepository;
import megane6.weplanet.repository.media.BoardMediaRepository;
import megane6.weplanet.repository.portal.ArtistProfileRepository;
import megane6.weplanet.repository.calendar.ArtistScheduleRepository;
import megane6.weplanet.repository.portal.PortalNoticeRepository;
import megane6.weplanet.service.main.FileStorageService;
import megane6.weplanet.service.community.CommunityUrls;
import megane6.weplanet.service.email.CommunityActivityNotifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final LiveCommentReportRepository liveCommentReportRepository;
    private final FileStorageService fileStorageService;
    private final CommunityActivityNotifier communityActivityNotifier; // 새 공지 → 팔로워 이메일
    private final MessageSource messageSource;
    private final CommunityUrls communityUrls; // 커뮤니티 영문 주소

    public static final int MAX_PINNED = 5;

    // 화면 언어 에러 메시지 조회
    private String msg(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

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
                .orElseThrow(() -> new IllegalArgumentException("error.notice.notFound"));
    }

    @Transactional(readOnly = true)
    public PortalNotice getPublishedNotice(User artist, Long noticeId) {
        PortalNotice notice = getNotice(artist, noticeId);
        if (!notice.isPublished()) {
            throw new IllegalArgumentException("error.notice.notFound");
        }
        return notice;
    }

    public PortalNotice saveNotice(User artist, Long noticeId, String title, String content, boolean published, boolean pinned) {
        validateText(title, msg("noticeForm.error.titleRequired"));
        validateText(content, msg("noticeForm.error.contentRequired"));
        boolean isNew = noticeId == null;
        PortalNotice notice = isNew
                ? PortalNotice.create(artist, title.trim(), content.trim(), published)
                : getNotice(artist, noticeId);
        // 수정 전 이미 공개된 공지인지
        boolean wasPublished = !isNew && notice.isPublished();
        if (noticeId != null) {
            notice.update(title, content, published);
        }
        applyPinState(artist, notice, pinned);
        PortalNotice saved = portalNoticeRepository.save(notice);
        // 비공개 → 공개로 바뀔 때만 팔로워에게 알린다.
        if (published && !wasPublished) {
            communityActivityNotifier.notifyNewNotice(artist, saved);
        }
        return saved;
    }

    public void reorderPinned(User artist, List<Long> ids) {
        List<PortalNotice> pinned = portalNoticeRepository.findByArtistAndPinnedTrueOrderByPinOrderAsc(artist);
        if (ids == null || ids.isEmpty() || ids.size() != pinned.size()) {
            throw new IllegalArgumentException("error.notice.pinnedOrderInvalid");
        }
        Map<Long, PortalNotice> byId = pinned.stream()
                .collect(Collectors.toMap(PortalNotice::getId, item -> item));
        List<PortalNotice> reordered = new ArrayList<>(ids.size());
        int order = 1;
        for (Long id : ids) {
            PortalNotice notice = byId.remove(id);
            if (notice == null) {
                throw new IllegalArgumentException("error.notice.pinnedOrderInvalid");
            }
            notice.applyPin(true, order++);
            reordered.add(notice);
        }
        if (!byId.isEmpty()) {
            throw new IllegalArgumentException("error.notice.pinnedOrderInvalid");
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
                throw new IllegalArgumentException(msg("noticeForm.error.pinnedLimit"));
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

    // 생일 일정은 한국어 기본 제목·설명이면 현재 언어 문구로 바꿔 보여준다.
    static final String BIRTHDAY_DEFAULT_TITLE_SUFFIX = " 생일";
    static final String BIRTHDAY_PROFILE_DESCRIPTION = "프로필에서 등록된 생일";

    public String displayTitle(ArtistSchedule schedule) {
        String title = schedule.getTitle();
        if (schedule.getCategory() != ScheduleCategory.BIRTHDAY) {
            return title;
        }
        String nickname = schedule.getArtist().getNickname();
        if (title == null || title.isBlank() || title.equals(nickname + BIRTHDAY_DEFAULT_TITLE_SUFFIX)) {
            return messageSource.getMessage("schedule.birthday.defaultTitle", new Object[]{nickname},
                    LocaleContextHolder.getLocale());
        }
        return title;
    }

    public String displayDescription(ArtistSchedule schedule) {
        String description = schedule.getDescription();
        if (schedule.getCategory() == ScheduleCategory.BIRTHDAY && BIRTHDAY_PROFILE_DESCRIPTION.equals(description)) {
            return msg("schedule.birthday.profileDescription");
        }
        return description;
    }

    private ScheduleEventView toEventView(ArtistSchedule schedule, LocalDateTime occurrenceAt) {
        return ScheduleEventView.from(schedule, occurrenceAt, displayTitle(schedule), displayDescription(schedule));
    }

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
                    ? "error.schedule.birthdayRequired"
                    : "error.schedule.dateTimeRequired");
        }
        if (resolved == ScheduleCategory.BIRTHDAY) {
            LocalDate birthDate = scheduleAt.toLocalDate();
            if (birthDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("portalProfile.error.birthDateFuture");
            }
            scheduleAt = birthDate.atTime(LocalTime.MIDNIGHT);
            if (title == null || title.isBlank()) {
                title = artist.getNickname() + BIRTHDAY_DEFAULT_TITLE_SUFFIX;
            }
        }
        validateText(title, "error.schedule.titleRequired");
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
                .map(item -> toEventView(item, item.getScheduleAt()))
                .forEach(events::add);

        appendBirthdayEventsInMonth(events, artist, month);
        events.sort(Comparator.comparing(ScheduleEventView::date).thenComparing(ScheduleEventView::time));
        return events;
    }

    private List<ScheduleEventView> expandScheduleEvents(List<ArtistSchedule> schedules) {
        int thisYear = Year.now().getValue();
        List<ScheduleEventView> events = new ArrayList<>();
        for (ArtistSchedule schedule : schedules) {
            if (schedule.getCategory() == ScheduleCategory.BIRTHDAY) {
                // 알림·공개 캘린더는 올해 생일만
                LocalDate birthDate = schedule.getScheduleAt().toLocalDate();
                if (thisYear >= birthDate.getYear()) {
                    appendBirthdayEvents(events, schedule, thisYear, thisYear);
                }
                continue;
            }
            events.add(toEventView(schedule, schedule.getScheduleAt()));
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
            events.add(toEventView(
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
            events.add(toEventView(birthday, occurrence.atTime(time)));
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
        item.put("createdAt", event.createdAt());
        return item;
    }

    public void deleteSchedule(User artist, Long scheduleId) {
        ArtistSchedule schedule = artistScheduleRepository.findById(scheduleId)
                .filter(item -> item.getArtist().getId().equals(artist.getId()))
                .orElseThrow(() -> new IllegalArgumentException("error.schedule.notFound"));
        artistScheduleRepository.delete(schedule);
    }

    public void rescheduleSchedule(User artist, Long scheduleId, LocalDate targetDate) {
        if (targetDate == null) {
            throw new IllegalArgumentException("error.schedule.moveDateRequired");
        }
        ArtistSchedule schedule = artistScheduleRepository.findById(scheduleId)
                .filter(item -> item.getArtist().getId().equals(artist.getId()))
                .orElseThrow(() -> new IllegalArgumentException("error.schedule.notFound"));

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
                throw new IllegalArgumentException("portalProfile.error.birthDateFuture");
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

    /** 커뮤니티 About 위젯용 (없으면 null) */
    @Transactional(readOnly = true)
    public String findIntro(User artist) {
        return artistProfileRepository.findByArtist(artist)
                .map(ArtistProfile::getIntro)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public String findLogoImageUrl(User artist) {
        if (artist == null) {
            return null;
        }
        return artistProfileRepository.findByArtist(artist)
                .map(ArtistProfile::getLogoImageUrl)
                .map(this::toPublicImageUrl)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public String findHeaderImageUrl(User artist) {
        if (artist == null) {
            return null;
        }
        return artistProfileRepository.findByArtist(artist)
                .map(ArtistProfile::getHeaderImageUrl)
                .map(this::toPublicImageUrl)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> logoImageUrlsByArtistIds(java.util.Collection<Long> artistIds) {
        if (artistIds == null || artistIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (Object[] row : artistProfileRepository.findLogoImageUrlsByArtistIds(artistIds)) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            String publicUrl = toPublicImageUrl(String.valueOf(row[1]));
            if (publicUrl != null) {
                result.put((Long) row[0], publicUrl);
            }
        }
        return result;
    }

    /** 저장된 파일명·URL 을 화면 경로로 변환 */
    public String toPublicImageUrl(String storedOrUrl) {
        if (storedOrUrl == null || storedOrUrl.isBlank()) {
            return null;
        }
        String value = storedOrUrl.trim();
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")) {
            return value;
        }
        return "/uploads/" + value;
    }

    private boolean isUploadedStoredName(String storedOrUrl) {
        if (storedOrUrl == null || storedOrUrl.isBlank()) {
            return false;
        }
        String value = storedOrUrl.trim();
        return !(value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/"));
    }

    private void deleteUploadedIfPresent(String storedOrUrl) {
        if (isUploadedStoredName(storedOrUrl)) {
            fileStorageService.delete(storedOrUrl.trim());
        }
    }

    @Transactional(readOnly = true)
    public ArtistCardView toArtistCard(User artist) {
        return communityUrls.withHomeUrl(ArtistCardView.from(artist, findLogoImageUrl(artist)));
    }

    @Transactional(readOnly = true)
    public List<ArtistCardView> toArtistCards(List<User> artists) {
        if (artists == null || artists.isEmpty()) {
            return List.of();
        }
        Map<Long, String> logos = logoImageUrlsByArtistIds(artists.stream().map(User::getId).toList());
        return communityUrls.withHomeUrls(artists.stream()
                .map(user -> ArtistCardView.from(user, logos.get(user.getId())))
                .toList());
    }

    public void updateProfile(User artist,
                              String nickname,
                              String email,
                              String realName,
                              String gender,
                              LocalDate birthDate,
                              String intro,
                              MultipartFile avatar,
                              MultipartFile background,
                              boolean removeAvatar,
                              boolean removeBackground) {
        validateText(nickname, msg("portalProfile.error.nicknameRequired"));
        if (nickname.trim().length() > 50) {
            throw new IllegalArgumentException(msg("portalProfile.error.nicknameTooLong"));
        }
        validateText(email, msg("portalProfile.error.emailRequired"));
        if (intro != null && intro.length() > 30) {
            throw new IllegalArgumentException(msg("portalProfile.error.introTooLong"));
        }

        artist.changePortalProfile(nickname.trim(), email.trim());
        if (realName != null && !realName.isBlank()) {
            artist.changeRealName(realName.trim());
        }
        artist.changeGender(parseGender(gender));
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(msg("portalProfile.error.birthDateFuture"));
        }
        artist.changeBirthDate(birthDate);
        syncBirthdaySchedule(artist, birthDate);

        ArtistProfile profile = getOrCreateProfile(artist);
        profile.updateIntro(intro);
        applyProfileImages(profile, avatar, background, removeAvatar, removeBackground);
        artistProfileRepository.save(profile);
    }

    /** 아티스트 쪽 계정의 커뮤니티 프로필 편집 - 포털 프로필(소개·사진·배경)을 고친다 (이름은 소속사 관리). */
    public void updateArtistCommunityProfile(User account,
                                             String intro,
                                             MultipartFile avatar,
                                             MultipartFile background,
                                             boolean removeAvatar,
                                             boolean removeBackground) {
        if (intro != null && intro.length() > 30) {
            throw new IllegalArgumentException("error.community.bioTooLong");
        }

        ArtistProfile profile = getOrCreateProfile(account);
        profile.updateIntro(intro);
        applyProfileImages(profile, avatar, background, removeAvatar, removeBackground);
        artistProfileRepository.save(profile);
    }

    // 프로필 사진·배경 교체·삭제 (포털과 커뮤니티 편집 공통)
    private void applyProfileImages(ArtistProfile profile,
                                    MultipartFile avatar,
                                    MultipartFile background,
                                    boolean removeAvatar,
                                    boolean removeBackground) {
        if (removeAvatar) {
            deleteUploadedIfPresent(profile.getLogoImageUrl());
            profile.clearLogoImage();
        } else if (avatar != null && !avatar.isEmpty()) {
            // 새 파일 저장이 끝난 뒤 옛 파일을 지운다.
            String newLogo = fileStorageService.storeImage(avatar);
            deleteUploadedIfPresent(profile.getLogoImageUrl());
            profile.replaceLogoImage(newLogo);
        }

        if (removeBackground) {
            deleteUploadedIfPresent(profile.getHeaderImageUrl());
            profile.clearHeaderImage();
        } else if (background != null && !background.isEmpty()) {
            String newHeader = fileStorageService.storeImage(background);
            deleteUploadedIfPresent(profile.getHeaderImageUrl());
            profile.replaceHeaderImage(newHeader);
        }
    }

    /** 프로필 생일을 캘린더 생일 일정과 동기화한다. */
    private void syncBirthdaySchedule(User artist, LocalDate birthDate) {
        List<ArtistSchedule> birthdays = artistScheduleRepository
                .findByArtistAndCategoryOrderByScheduleAtAsc(artist, ScheduleCategory.BIRTHDAY);
        if (birthDate == null) {
            return;
        }
        String title = artist.getNickname() + BIRTHDAY_DEFAULT_TITLE_SUFFIX;
        LocalDateTime at = birthDate.atTime(LocalTime.MIDNIGHT);
        if (birthdays.isEmpty()) {
            artistScheduleRepository.save(ArtistSchedule.create(
                    artist,
                    ScheduleCategory.BIRTHDAY,
                    title,
                    BIRTHDAY_PROFILE_DESCRIPTION,
                    null,
                    null,
                    at
            ));
            return;
        }
        ArtistSchedule first = birthdays.get(0);
        first.update(
                ScheduleCategory.BIRTHDAY,
                title,
                first.getDescription() != null ? first.getDescription() : BIRTHDAY_PROFILE_DESCRIPTION,
                first.getLocation(),
                first.getTicketUrl(),
                at
        );
    }

    private Gender parseGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        return switch (gender.trim().toUpperCase()) {
            case "MALE", "남" -> Gender.MALE;
            case "FEMALE", "여" -> Gender.FEMALE;
            case "OTHER", "UNKNOWN", "미상" -> Gender.OTHER;
            default -> throw new IllegalArgumentException(msg("portalProfile.error.genderInvalid"));
        };
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
        return reportRepository.countByPost_Artist(artist)
                + commentReportRepository.countByComment_Post_Artist(artist)
                + liveCommentReportRepository.countByArtist(artist);
    }

    private void validateText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
