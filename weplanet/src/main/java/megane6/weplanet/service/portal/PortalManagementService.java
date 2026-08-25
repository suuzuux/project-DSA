package megane6.weplanet.service.portal;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.ArtistProfile;
import megane6.weplanet.domain.entity.portal.ArtistSchedule;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.MembershipRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.media.BoardMediaRepository;
import megane6.weplanet.repository.portal.ArtistProfileRepository;
import megane6.weplanet.repository.portal.ArtistScheduleRepository;
import megane6.weplanet.repository.portal.PortalNoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

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

    @Transactional(readOnly = true)
    public List<PortalNotice> getNotices(User artist) {
        return portalNoticeRepository.findByArtistOrderByCreatedAtDesc(artist);
    }

    @Transactional(readOnly = true)
    public List<PortalNotice> getPublishedNotices(User artist) {
        return portalNoticeRepository.findByArtistAndPublishedTrueOrderByCreatedAtDesc(artist);
    }

    @Transactional(readOnly = true)
    public PortalNotice getNotice(User artist, Long noticeId) {
        return portalNoticeRepository.findByIdAndArtist(noticeId, artist)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다."));
    }

    public PortalNotice saveNotice(User artist, Long noticeId, String title, String content, boolean published) {
        validateText(title, "제목을 입력해주세요.");
        validateText(content, "본문을 입력해주세요.");
        PortalNotice notice = noticeId == null
                ? PortalNotice.create(artist, title.trim(), content.trim(), published)
                : getNotice(artist, noticeId);
        if (noticeId != null) {
            notice.update(title, content, published);
        }
        return portalNoticeRepository.save(notice);
    }

    public void deleteNotice(User artist, Long noticeId) {
        portalNoticeRepository.delete(getNotice(artist, noticeId));
    }

    @Transactional(readOnly = true)
    public List<ArtistSchedule> getSchedules(User artist) {
        return artistScheduleRepository.findByArtistOrderByScheduleAtAsc(artist);
    }

    @Transactional(readOnly = true)
    public List<ArtistSchedule> getSchedulesInMonth(User artist, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59);
        return artistScheduleRepository.findByArtistAndScheduleAtBetweenOrderByScheduleAtAsc(artist, start, end);
    }

    public void createSchedule(User artist, String title, String description, String location, LocalDateTime scheduleAt) {
        validateText(title, "일정 제목을 입력해주세요.");
        if (scheduleAt == null) {
            throw new IllegalArgumentException("일정 일시를 입력해주세요.");
        }
        artistScheduleRepository.save(ArtistSchedule.create(artist, title, description, location, scheduleAt));
    }

    public void deleteSchedule(User artist, Long scheduleId) {
        ArtistSchedule schedule = artistScheduleRepository.findById(scheduleId)
                .filter(item -> item.getArtist().getId().equals(artist.getId()))
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));
        artistScheduleRepository.delete(schedule);
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
